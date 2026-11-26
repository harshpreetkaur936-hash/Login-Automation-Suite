# Login Automation Suite

## Project Overview
Login Automation Suite is a Java-based web application testing project that uses Selenium WebDriver to automate the login testing process. It helps test login functionality through automated browser interactions and generates test reports.

## Technologies Used
- Java
- Selenium WebDriver
- Maven
- HTML
- CSS
- JavaScript
- Chrome Browser

## Features
- Automated login testing using Selenium WebDriver.
- Browser-based test execution.
- Testing of login functionality.
- HTML and CSV test reports.
- Simple web interface for demonstration.
- Organized Maven project structure.

## Project Structure

```text
Login-Automation-Suite/
│
├── pom.xml
├── .gitignore
├── reports/
│   ├── test-report.csv
│   └── test-report.html
│
└── src/
    ├── main/
    │   ├── java/com/loginautomation/
    │   │   ├── App.java
    │   │   └── SeleniumLoginTest.java
    │   │
    │   └── resources/static/
    │       ├── demo.html
    │       ├── index.html
    │       ├── login.html
    │       └── reports/
    │
    └── test/
        └── java/com/loginautomation/
            └── AppTest.java
```

## Requirements
- Java Development Kit (JDK)
- Apache Maven
- Google Chrome Browser
- Visual Studio Code or any Java IDE

## How to Run
1. Clone or download this repository.
2. Open the project in Visual Studio Code.
3. Make sure Java and Maven are configured.
4. Install the Maven dependencies.
5. Ensure the demo application is running at the URL configured in `SeleniumLoginTest.java`.
6. Run the Selenium test class.
7. Check the generated reports in the `reports` folder.

## Test Reports
The project includes:
- HTML Test Report
- CSV Test Report

These reports help review the results of automated test execution.

## Author
Harshpreet Kaur

## GitHub Repository
[Login Automation Suite](https://github.com/harshpreetkaur936-hash/Login-Automation-Suite)
