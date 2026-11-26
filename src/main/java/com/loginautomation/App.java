package com.loginautomation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class App {

    private static final int TOTAL = 25;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private static volatile boolean running = false;
    private static volatile String currentTest = "Waiting to start";

    private static int completed = 0;
    private static int passed = 0;
    private static int failed = 0;

    private static final Map<String, String[]> results =
            new LinkedHashMap<>();

    private static final Set<String> activeSessions =
            ConcurrentHashMap.newKeySet();

    private static final SecureRandom secureRandom = new SecureRandom();

    private static final Path STATIC_DIR =
            Path.of("src/main/resources/static")
                    .toAbsolutePath().normalize();

    public static synchronized void resetProgress() {
        completed = 0;
        passed = 0;
        failed = 0;
        currentTest = "Starting automation";
        results.clear();
        running = true;
    }

    public static synchronized void updateProgress(
            String name, int done, int pass, int fail) {

        currentTest = name;
        completed = Math.min(done, TOTAL);
        passed = pass;
        failed = fail;
    }

    public static synchronized void updateTestResult(
            String name, String status, String message) {

        results.put(name, new String[]{status, message});
    }

    private static synchronized String progressJson() {

        StringBuilder json = new StringBuilder();

        json.append("{")
                .append("\"running\":").append(running).append(",")
                .append("\"currentTest\":\"")
                .append(escape(currentTest)).append("\",")
                .append("\"completed\":").append(completed).append(",")
                .append("\"total\":").append(TOTAL).append(",")
                .append("\"passed\":").append(passed).append(",")
                .append("\"failed\":").append(failed).append(",")
                .append("\"results\":{");

        boolean first = true;

        for (Map.Entry<String, String[]> entry : results.entrySet()) {

            if (!first) {
                json.append(",");
            }

            first = false;

            json.append("\"").append(escape(entry.getKey())).append("\":{")
                    .append("\"status\":\"")
                    .append(escape(entry.getValue()[0])).append("\",")
                    .append("\"message\":\"")
                    .append(escape(entry.getValue()[1])).append("\"")
                    .append("}");
        }

        json.append("}}");

        return json.toString();
    }

    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] data
    ) throws IOException {

        exchange.getResponseHeaders().set(
                "Content-Type", contentType);

        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static void sendText(
            HttpExchange exchange,
            int status,
            String content
    ) throws IOException {

        send(
                exchange,
                status,
                "application/json; charset=UTF-8",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {

        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static String getJsonValue(String json, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return "";
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    private static String getSessionToken(HttpExchange exchange) {

        String cookieHeader =
                exchange.getRequestHeaders().getFirst("Cookie");

        if (cookieHeader == null) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {

            String trimmed = cookie.trim();

            if (trimmed.startsWith("LOGIN_SESSION=")) {
                return trimmed.substring("LOGIN_SESSION=".length());
            }
        }

        return null;
    }

    private static boolean isAuthenticated(HttpExchange exchange) {

        String token = getSessionToken(exchange);

        return token != null && activeSessions.contains(token);
    }

    private static boolean requireAuthentication(
            HttpExchange exchange
    ) throws IOException {

        if (isAuthenticated(exchange)) {
            return true;
        }

        sendText(
                exchange,
                401,
                "{\"error\":\"Please login to access this page\"}"
        );

        exchange.close();
        return false;
    }

    private static void serveFile(
            HttpExchange exchange,
            String relativePath
    ) throws IOException {

        Path file = STATIC_DIR.resolve(relativePath).normalize();

        if (!file.startsWith(STATIC_DIR)
                || !Files.exists(file)
                || Files.isDirectory(file)) {

            sendText(exchange, 404, "{\"error\":\"File not found\"}");
            return;
        }

        String contentType;

        if (relativePath.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (relativePath.endsWith(".csv")) {
            contentType = "text/csv; charset=UTF-8";
        } else if (relativePath.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (relativePath.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        } else {
            contentType = "application/octet-stream";
        }

        send(
                exchange,
                200,
                contentType,
                Files.readAllBytes(file)
        );
    }

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 8080),
                0
        );

        // ADMIN LOGIN
        server.createContext("/api/login", exchange -> {

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405,
                        "{\"error\":\"POST required\"}");
                exchange.close();
                return;
            }

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            String username = getJsonValue(body, "username");
            String password = getJsonValue(body, "password");

            if (ADMIN_USERNAME.equals(username)
                    && ADMIN_PASSWORD.equals(password)) {

                byte[] randomBytes = new byte[32];
                secureRandom.nextBytes(randomBytes);

                String token = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(randomBytes);

                activeSessions.add(token);

                exchange.getResponseHeaders().add(
                        "Set-Cookie",
                        "LOGIN_SESSION=" + token
                                + "; Path=/; HttpOnly; SameSite=Strict"
                );

                sendText(exchange, 200,
                        "{\"success\":true,\"message\":\"Login successful\"}");

            } else {

                sendText(exchange, 401,
                        "{\"success\":false,"
                                + "\"error\":\"Invalid username or password\"}");
            }

            exchange.close();
        });

        // CHECK LOGIN STATUS
        server.createContext("/api/auth-status", exchange -> {

            boolean authenticated = isAuthenticated(exchange);

            sendText(
                    exchange,
                    200,
                    "{\"authenticated\":" + authenticated + "}"
            );

            exchange.close();
        });

        // LOGOUT
        server.createContext("/api/logout", exchange -> {

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405,
                        "{\"error\":\"POST required\"}");
                exchange.close();
                return;
            }

            String token = getSessionToken(exchange);

            if (token != null) {
                activeSessions.remove(token);
            }

            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    "LOGIN_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"
            );

            sendText(exchange, 200,
                    "{\"success\":true,\"message\":\"Logged out\"}");

            exchange.close();
        });

        // SERVER STATUS
        server.createContext("/api/status", exchange -> {

            sendText(exchange, 200, "{\"status\":\"online\"}");
            exchange.close();
        });

        // PROTECTED PROGRESS API
        server.createContext("/api/progress", exchange -> {

            if (!requireAuthentication(exchange)) {
                return;
            }

            sendText(exchange, 200, progressJson());
            exchange.close();
        });

        // PROTECTED RUN TESTS API
        server.createContext("/api/run-tests", exchange -> {

            if (!requireAuthentication(exchange)) {
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405,
                        "{\"error\":\"POST required\"}");
                exchange.close();
                return;
            }

            synchronized (App.class) {

                if (running) {
                    sendText(exchange, 409,
                            "{\"error\":\"Automation is already running\"}");
                    exchange.close();
                    return;
                }

                resetProgress();
            }

            sendText(exchange, 200,
                    "{\"message\":\"Automation started\"}");
            exchange.close();

            CompletableFuture.runAsync(() -> {

                try {
                    SeleniumLoginTest.runAllTests();

                } finally {
                    synchronized (App.class) {
                        running = false;
                        currentTest = "Automation completed";
                    }
                }
            });
        });

        // PROTECTED REPORTS
        server.createContext("/reports/", exchange -> {

            if (!requireAuthentication(exchange)) {
                return;
            }

            String path = exchange.getRequestURI().getPath()
                    .substring("/reports/".length());

            serveFile(exchange, "reports/" + path);
            exchange.close();
        });

        // STUDENT PORTAL FOR SELENIUM
        server.createContext("/demo", exchange -> {

            serveFile(exchange, "demo.html");
            exchange.close();
        });

        // PROTECTED DASHBOARD
        server.createContext("/dashboard", exchange -> {

            if (!isAuthenticated(exchange)) {
                redirect(exchange, "/");
                return;
            }

            serveFile(exchange, "index.html");
            exchange.close();
        });

        // ROOT: LOGIN PAGE
        server.createContext("/", exchange -> {

            String path = URLDecoder.decode(
                    exchange.getRequestURI().getPath(),
                    StandardCharsets.UTF_8
            );

            if (path.equals("/") || path.equals("/index.html")) {

                if (isAuthenticated(exchange)) {
                    redirect(exchange, "/dashboard");
                    return;
                }

                serveFile(exchange, "login.html");

            } else {

                if (!isAuthenticated(exchange)) {
                    redirect(exchange, "/");
                    return;
                }

                serveFile(exchange, path.substring(1));
            }

            exchange.close();
        });

        server.setExecutor(null);
        server.start();

        System.out.println("================================");
        System.out.println("LOGIN AUTOMATION SUITE STARTED");
        System.out.println("Login: http://127.0.0.1:8080");
        System.out.println("Dashboard: http://127.0.0.1:8080/dashboard");
        System.out.println("================================");
    }
}