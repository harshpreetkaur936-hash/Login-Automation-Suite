package com.loginautomation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SeleniumLoginTest {

    private static final String APP_URL =
            "http://127.0.0.1:8080/demo";

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test123";

    private static final int TOTAL_TESTS = 25;

    private static final Map<String, String[]> results =
            new LinkedHashMap<>();

    private static int passed = 0;
    private static int failed = 0;
    private static int completed = 0;

    private static final Path REPORT_DIR =
            Paths.get("src/main/resources/static/reports");

    private static final Path HTML_REPORT =
            REPORT_DIR.resolve("test-report.html");

    private static final Path CSV_REPORT =
            REPORT_DIR.resolve("test-report.csv");

    private interface TestAction {
        void execute(WebDriver driver, WebDriverWait wait)
                throws Exception;
    }

    public static String runAllTests() {

        results.clear();
        passed = 0;
        failed = 0;
        completed = 0;

        WebDriver driver = null;

        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--start-maximized");

            driver = new ChromeDriver(options);

            WebDriverWait wait =
                    new WebDriverWait(driver, Duration.ofSeconds(8));

            driver.get(APP_URL);

            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.id("loginForm")));

            // PHASE 1: SEVEN INVALID LOGIN TESTS

            runTest(driver, wait, "Wrong Password",
                    "Invalid password should be rejected.",
                    (d, w) -> {
                        login(d, USERNAME, "wrong123");
                        verifyInvalidLogin(d, w,
                                "Wrong password was accepted.");
                    });

            runTest(driver, wait, "Invalid Username",
                    "Unknown username should be rejected.",
                    (d, w) -> {
                        login(d, "unknownuser", PASSWORD);
                        verifyInvalidLogin(d, w,
                                "Unknown username was accepted.");
                    });

            runTest(driver, wait, "Both Fields Empty",
                    "Login should require username and password.",
                    (d, w) -> {
                        clearLoginFields(d);
                        d.findElement(By.id("loginBtn")).click();

                        if (!isRequired(d, "username")
                                || !isRequired(d, "password")) {
                            throw new AssertionError(
                                    "Required validation is missing.");
                        }

                        checkVisible(d, "loginForm",
                                "Login form disappeared.");
                    });

            runTest(driver, wait, "Username Empty",
                    "Login should require a username.",
                    (d, w) -> {
                        clearLoginFields(d);
                        d.findElement(By.id("password"))
                                .sendKeys(PASSWORD);
                        d.findElement(By.id("loginBtn")).click();

                        if (!isRequired(d, "username")) {
                            throw new AssertionError(
                                    "Username required validation failed.");
                        }
                    });

            runTest(driver, wait, "Password Empty",
                    "Login should require a password.",
                    (d, w) -> {
                        clearLoginFields(d);
                        d.findElement(By.id("username"))
                                .sendKeys(USERNAME);
                        d.findElement(By.id("loginBtn")).click();

                        if (!isRequired(d, "password")) {
                            throw new AssertionError(
                                    "Password required validation failed.");
                        }
                    });

            runTest(driver, wait, "Username Case Sensitivity",
                    "Incorrect username case should be rejected.",
                    (d, w) -> {
                        login(d, "TESTER", PASSWORD);
                        verifyInvalidLogin(d, w,
                                "Uppercase username was accepted.");
                    });

            runTest(driver, wait, "Password Case Sensitivity",
                    "Incorrect password case should be rejected.",
                    (d, w) -> {
                        login(d, USERNAME, "TEST123");
                        verifyInvalidLogin(d, w,
                                "Uppercase password was accepted.");
                    });

            // PHASE 2: VALID LOGIN

            runTest(driver, wait, "Valid Login",
                    "Correct credentials should open the dashboard.",
                    (d, w) -> {
                        login(d, USERNAME, PASSWORD);

                        w.until(ExpectedConditions.visibilityOfElementLocated(
                                By.id("dashboard")));

                        checkVisible(d, "dashboard",
                                "Student dashboard did not open.");
                    });

            // PHASE 3: DASHBOARD TESTS

            runTest(driver, wait, "Dashboard Display",
                    "Student dashboard should be visible.",
                    (d, w) -> {
                        checkVisible(d, "dashboard",
                                "Dashboard is not visible.");
                    });

            runSectionTest(driver, wait, "Timetable", 0);

            runSectionTest(driver, wait,
                    "Assignments and Pending Work", 1);

            runSectionTest(driver, wait, "Exam Schedule", 2);

            runSectionTest(driver, wait, "Study Planner", 3);

            runSectionTest(driver, wait, "Attendance", 4);

            runSectionTest(driver, wait, "To Do List", 5);

            runSectionTest(driver, wait, "Results", 6);

            runSectionTest(driver, wait, "Study Materials", 7);

            runSectionTest(driver, wait, "Notices", 8);

            runSectionTest(driver, wait, "Student Profile", 9);

            runTest(driver, wait, "To Do Input",
                    "To Do input should accept text.",
                    (d, w) -> {
                        showSection(d, w, 5);

                        WebElement input =
                                d.findElement(By.id("todoInput"));

                        input.clear();
                        input.sendKeys("Selenium automation task");

                        String value = input.getDomProperty("value");

                        if (!"Selenium automation task".equals(value)) {
                            throw new AssertionError(
                                    "To Do input did not accept text.");
                        }
                    });

            runTest(driver, wait, "To Do Form",
                    "To Do form should be visible and enabled.",
                    (d, w) -> {
                        showSection(d, w, 5);

                        WebElement form =
                                d.findElement(By.id("todoForm"));

                        if (!form.isDisplayed() || !form.isEnabled()) {
                            throw new AssertionError(
                                    "To Do form is unavailable.");
                        }
                    });

            runTest(driver, wait, "Section Navigation",
                    "Previous and Next buttons should navigate sections.",
                    (d, w) -> {
                        showSection(d, w, 5);

                        d.findElement(By.id("nextSection")).click();

                        w.until(ExpectedConditions.textToBePresentInElementLocated(
                                By.id("sectionCounter"),
                                "Section 7 of 10"));

                        d.findElement(By.id("previousSection")).click();

                        w.until(ExpectedConditions.textToBePresentInElementLocated(
                                By.id("sectionCounter"),
                                "Section 6 of 10"));
                    });

            runTest(driver, wait, "Add To Do Task",
                    "A new task should be added to the To Do list.",
                    (d, w) -> {
                        showSection(d, w, 5);

                        d.findElement(By.id("todoInput"))
                                .sendKeys("Complete Selenium project");

                        d.findElement(By.id("todoForm")).submit();

                        w.until(ExpectedConditions.textToBePresentInElementLocated(
                                By.id("todoRows"),
                                "Complete Selenium project"));
                    });

            // PHASE 4: REPORT TESTS

            runTest(driver, wait, "HTML Report Generation",
                    "HTML report should be generated and displayed.",
                    (d, w) -> {
                        generateHtmlReport();

                        if (!Files.exists(HTML_REPORT)
                                || Files.size(HTML_REPORT) == 0) {
                            throw new AssertionError(
                                    "HTML report was not generated.");
                        }

                        d.get(HTML_REPORT.toAbsolutePath()
                                .toUri().toString());

                        w.until(ExpectedConditions.titleContains(
                                "Automation Test Report"));

                        if (!d.getPageSource().contains(
                                "Test Execution Report")) {
                            throw new AssertionError(
                                    "HTML report content is missing.");
                        }
                    });

            runTest(driver, wait, "CSV Report Generation",
                    "CSV report should be generated and validated.",
                    (d, w) -> {
                        generateCsvReport();

                        if (!Files.exists(CSV_REPORT)
                                || Files.size(CSV_REPORT) == 0) {
                            throw new AssertionError(
                                    "CSV report was not generated.");
                        }

                        String csv = Files.readString(
                                CSV_REPORT, StandardCharsets.UTF_8);

                        if (!csv.contains("Test Case")
                                || !csv.contains("Status")
                                || !csv.contains("Expected / Actual / Error")) {
                            throw new AssertionError(
                                    "CSV report headings are missing.");
                        }

                        // Display CSV content in the browser.
                        d.get("data:text/html;charset=utf-8,"
                                + java.net.URLEncoder.encode(
                                        "<html><body><h2>CSV Test Report</h2>"
                                        + "<pre>"
                                        + escapeHtml(csv)
                                        + "</pre></body></html>",
                                        StandardCharsets.UTF_8));
                    });

        } catch (Throwable e) {
            System.out.println("MAIN EXECUTION ERROR: " + errorText(e));

        } finally {
            System.out.println("\n========== FINAL SUMMARY ==========");
            System.out.println("Total: " + TOTAL_TESTS);
            System.out.println("Passed: " + passed);
            System.out.println("Failed: " + failed);
            System.out.println("Completed: " + completed);

            // Close Chrome after all test cases and report displays finish.
            if (driver != null) {
                try {
                    driver.quit();
                    System.out.println("Chrome browser closed successfully.");
                } catch (Exception closeError) {
                    System.out.println("Browser close warning: "
                            + errorText(closeError));
                }
            }
        }

        App.updateProgress(
                "All 25 tests completed",
                completed,
                passed,
                failed
        );

        return "{"
                + "\"status\":\"" + (failed == 0 ? "passed" : "failed") + "\","
                + "\"message\":\"All 25 tests completed\","
                + "\"passed\":" + passed + ","
                + "\"failed\":" + failed + ","
                + "\"results\":" + toJson()
                + "}";
    }

    // RUN ONE TEST AND SAVE ITS RESULT

    private static void runTest(
            WebDriver driver,
            WebDriverWait wait,
            String name,
            String expected,
            TestAction action) {

        String status = "FAIL";
        String actual = "Test did not complete.";
        String error = "No Error";

        App.updateProgress(
                name + " - RUNNING",
                completed,
                passed,
                failed
        );

        System.out.println("\nStarting Test: " + name);
        System.out.println("Expected: " + expected);

        // Make the RUNNING state visible before the test starts.
        pause(1000);

        try {
            action.execute(driver, wait);
            status = "PASS";
            actual = "Verified successfully: " + expected;
            passed++;
        } catch (Throwable e) {
            actual = "Test failed: " + errorText(e);
            error = errorText(e);
            failed++;
        }

        completed++;

        String message =
                "Expected: " + expected
                + " | Actual: " + actual
                + " | Error: " + error;

        saveResult(name, status, message);

        System.out.println("Status: " + status);
        System.out.println("Actual: " + actual);
        System.out.println("Error: " + error);

        // Mandatory 5-second pause after EVERY test case.
        pause(1000);
    }

    private static void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // DASHBOARD SECTION TEST

    private static void runSectionTest(
            WebDriver driver,
            WebDriverWait wait,
            String name,
            int sectionIndex) {

        runTest(driver, wait, name,
                name + " section should open and display content.",
                (d, w) -> {

                    showSection(d, w, sectionIndex);

                    String[] sectionIds = {
                        "timetableSection",
                        "pendingSection",
                        "examSection",
                        "studySection",
                        "attendanceSection",
                        "todoSection",
                        "resultsSection",
                        "materialsSection",
                        "noticesSection",
                        "profileSection"
                    };

                    WebElement section = d.findElement(
                            By.id(sectionIds[sectionIndex]));

                    if (!section.isDisplayed()) {
                        throw new AssertionError(
                                name + " section is not visible.");
                    }

                    if (section.getText().trim().isEmpty()) {
                        throw new AssertionError(
                                name + " section contains no content.");
                    }
                });
    }

    // NAVIGATE TO A PARTICULAR DASHBOARD SECTION

    private static void showSection(
            WebDriver driver,
            WebDriverWait wait,
            int targetIndex) {

        for (int i = 0; i < 10; i++) {

            String counter = driver.findElement(
                    By.id("sectionCounter")).getText();

            int currentIndex = Integer.parseInt(
                    counter.replaceAll("\\D*(\\d+).*", "$1"));

            int targetSection = targetIndex + 1;

            if (currentIndex == targetSection) {
                return;
            }

            if (currentIndex < targetSection) {
                driver.findElement(By.id("nextSection")).click();
            } else {
                driver.findElement(By.id("previousSection")).click();
            }

            int expectedSection = currentIndex < targetSection
                    ? currentIndex + 1
                    : currentIndex - 1;

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.id("sectionCounter"),
                    "Section " + expectedSection + " of 10"));
        }

        throw new AssertionError(
                "Could not navigate to section " + (targetIndex + 1));
    }

    // LOGIN HELPERS

    private static void login(
            WebDriver driver,
            String username,
            String password) {

        clearLoginFields(driver);

        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("loginBtn")).click();
    }

    private static void clearLoginFields(WebDriver driver) {

        driver.findElement(By.id("username")).clear();
        driver.findElement(By.id("password")).clear();
    }

    private static boolean isRequired(WebDriver driver, String id) {

        String required = driver.findElement(By.id(id))
                .getAttribute("required");

        return required != null;
    }

    private static void verifyInvalidLogin(
            WebDriver driver,
            WebDriverWait wait,
            String failureMessage) {

        wait.until(d -> {
            String message = d.findElement(By.id("loginMessage"))
                    .getText().trim();

            return !message.isEmpty()
                    || !d.findElement(By.id("loginForm")).isDisplayed();
        });

        if (driver.findElement(By.id("dashboard")).isDisplayed()) {
            throw new AssertionError(
                    "Invalid credentials opened the dashboard.");
        }

        String message = driver.findElement(By.id("loginMessage"))
                .getText().trim();

        if (message.isEmpty()) {
            throw new AssertionError(failureMessage);
        }
    }

    private static void checkVisible(
            WebDriver driver,
            String id,
            String error) {

        WebElement element = driver.findElement(By.id(id));

        if (!element.isDisplayed()) {
            throw new AssertionError(error);
        }
    }

    // SAVE TEST RESULT

    private static void saveResult(
            String name,
            String status,
            String message) {

        results.put(name, new String[]{status, message});

        App.updateTestResult(name, status, message);

        App.updateProgress(
                name + " - " + status,
                completed,
                passed,
                failed
        );
    }

    private static String errorText(Throwable e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName()
                    + ": No additional error details available.";
        }

        return e.getClass().getSimpleName() + ": " + message;
    }

    // GENERATE HTML REPORT

    private static void generateHtmlReport() throws IOException {

        Files.createDirectories(REPORT_DIR);

        StringBuilder html = new StringBuilder();

        html.append("""
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>Automation Test Report</title>
                <style>
                body {
                    font-family: Arial;
                    background: #101827;
                    color: white;
                    padding: 25px;
                }
                h1 { color: #60a5fa; }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 20px;
                }
                th, td {
                    padding: 12px;
                    border: 1px solid #334155;
                    text-align: left;
                }
                th { background: #1e3a5f; }
                .PASS { color: #4ade80; }
                .FAIL { color: #f87171; }
                </style>
                </head>
                <body>
                <h1>Test Execution Report</h1>
                """);

        html.append("<h3>Total Tests: ")
                .append(TOTAL_TESTS).append("</h3>");

        html.append("<h3>Passed: ")
                .append(passed).append("</h3>");

        html.append("<h3>Failed: ")
                .append(failed).append("</h3>");

        html.append("""
                <table>
                <tr>
                <th>Test Case</th>
                <th>Status</th>
                <th>Expected / Actual / Error</th>
                </tr>
                """);

        for (Map.Entry<String, String[]> entry : results.entrySet()) {

            html.append("<tr><td>")
                    .append(escapeHtml(entry.getKey()))
                    .append("</td><td class=\"")
                    .append(escapeHtml(entry.getValue()[0]))
                    .append("\">")
                    .append(escapeHtml(entry.getValue()[0]))
                    .append("</td><td>")
                    .append(escapeHtml(entry.getValue()[1]))
                    .append("</td></tr>");
        }

        html.append("</table></body></html>");

        Files.writeString(
                HTML_REPORT,
                html.toString(),
                StandardCharsets.UTF_8
        );
    }

    // GENERATE CSV REPORT

    private static void generateCsvReport() throws IOException {

        Files.createDirectories(REPORT_DIR);

        StringBuilder csv = new StringBuilder();

        csv.append("Test Case,Status,Expected / Actual / Error\n");

        for (Map.Entry<String, String[]> entry : results.entrySet()) {

            csv.append(csvValue(entry.getKey())).append(",");
            csv.append(csvValue(entry.getValue()[0])).append(",");
            csv.append(csvValue(entry.getValue()[1])).append("\n");
        }

        Files.writeString(
                CSV_REPORT,
                csv.toString(),
                StandardCharsets.UTF_8
        );
    }

    private static String csvValue(String value) {

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String escapeHtml(String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // CONVERT RESULTS TO JSON FOR DASHBOARD

    private static String toJson() {

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, String[]> entry : results.entrySet()) {

            if (!first) {
                json.append(",");
            }

            json.append("\"")
                    .append(escapeJson(entry.getKey()))
                    .append("\":{");

            json.append("\"status\":\"")
                    .append(escapeJson(entry.getValue()[0]))
                    .append("\",");

            json.append("\"message\":\"")
                    .append(escapeJson(entry.getValue()[1]))
                    .append("\"}");

            first = false;
        }

        json.append("}");
        return json.toString();
    }

    private static String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    public static void main(String[] args) throws Exception {
    SeleniumLoginTest test = new SeleniumLoginTest();
    test.runAllTests();
}
}