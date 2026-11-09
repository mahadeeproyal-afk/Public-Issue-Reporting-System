# Public Issue Reporting System

A Java 21 Swing desktop application for submitting civic complaints, tracking progress, and managing resolution through an administrator workflow.

## Features

- Citizen registration and role-specific login with BCrypt password hashing.
- Citizen complaint submission, private complaint list, and owner-only details/history.
- Public tracking by tracking code with a limited response: code, title, category, status, and submission time.
- Administrator complaint search and status filtering, department assignment, status updates, reporter contact information, and complaint history.
- A shared civic light theme across login, registration, tracking, dashboard, profile, and complaint detail screens.
- Session-aware service authorization and transactional status/history updates.

## Technology and structure

- Java 21, Maven, Swing, JDBC, MySQL 8, jBCrypt, JUnit 5.
- `model/`: users, complaints, departments, and public tracking response.
- `dao/`: JDBC persistence.
- `service/`: authentication, session, complaint authorization, and management.
- `ui/`: login, registration, public tracker, and role-aware dashboard.
- `src/test/`: isolated MySQL integration tests and test-only schema setup.

## Prerequisites

- JDK 21 (verify with `java -version`).
- Maven 3.9 or later (verify with `mvn -version`).
- MySQL 8 for running the desktop application and database-backed integration tests.

## Database setup and configuration

The application expects a database with `app_users`, `issues`, `departments`, and `issue_history` tables matching the DAO queries. No production/application schema bootstrap script is included, and the project does not run migrations automatically. Ask the project maintainer for the approved local schema provisioning procedure; do not point the test setup script at an application or production database.

For local application use, create `src/main/resources/config.properties` (ignored by Git) with the JDBC URL only:

```properties
db.url=jdbc:mysql://localhost:3306/civic_issue_db
```

Set production credentials in the PowerShell session that will launch Maven. The password prompt does not echo the password or put its value in shell history:

```powershell
$env:CIVIC_ISSUE_DB_USERNAME = Read-Host "Database username"
$secureDatabasePassword = Read-Host "Database password" -AsSecureString
$env:CIVIC_ISSUE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secureDatabasePassword).Password
Remove-Variable secureDatabasePassword
```

Then run the application from that same PowerShell session. Do not put credentials in `config.properties`, source control, or command-line arguments. Administrators must be provisioned by a trusted database administrator; the public registration screen creates citizen accounts only.

## Build and run

```powershell
mvn clean package
mvn exec:java
```

The configured Maven entry point is `com.publicissues.Main`, which opens the Swing login screen. The desktop application requires a graphical session and an application database configured as described above.

## Test setup and execution

Integration tests use only the separate `civic_issue_test_db` catalog. Read `src/test/resources/TEST_DATABASE.md`, provision that catalog using `src/test/resources/sql/setup_civic_issue_test_db.sql`, and set `CIVIC_ISSUE_TEST_DB_USERNAME` and `CIVIC_ISSUE_TEST_DB_PASSWORD` to credentials scoped to that test catalog. The setup SQL creates synthetic test users and departments; the seeded password values are sentinels and cannot be used to log in. Never use production credentials or run the test SQL against production.

Surefire turns on test mode. `DBConnection` requires the exact test URL, test-only credential environment variables, and the verified `civic_issue_test_db` catalog; it does not fall back to application credentials.

```powershell
mvn test
```

## User workflows

**Citizen:** Register from the login screen, sign in as Citizen, submit a complaint with title, category, location, and description, retain the generated tracking code, then use the dashboard to view personal complaints, details, and history. The profile screen currently displays account information; editing is not implemented.

**Public tracking:** From the login screen, select “Track a complaint” to open the unauthenticated tracking screen. Enter a valid tracking code to see the code, title, category, current status, and submission time. The screen does not include location, description, account details, remarks, or history.

**Administrator:** Sign in using a separately provisioned administrator account. Search/filter complaints, open details and history, review reporter contact information, assign a configured department, update status, and log out. Public registration cannot create an administrator.

## Demonstration checklist

Use only synthetic records in a locally approved demonstration database and non-production accounts.

1. Launch the application and register/sign in as a citizen.
2. Submit a synthetic complaint and note its generated tracking code.
3. Show the citizen complaint list and open the complaint details/history.
4. Log out and use public tracking with the saved code.
5. Sign in with a separately provisioned administrator account.
6. Find the complaint, assign a department, and update its status.
7. Show the reporter details and the recorded history entry.
8. Log out and confirm return to the login screen.

## Known limitations

- The production/application schema provisioning instructions are maintained outside this repository; no automatic migration is performed.
- Profile information is display-only.
- Valid statuses are `SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, and `REJECTED`. No approved transition matrix exists, so the service accepts any different valid status.
- Database-backed tests require a running MySQL instance and credentials restricted to `civic_issue_test_db`.
