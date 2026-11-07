# Issue assignment and resolution module

This module uses the repository's existing JDBC connection, DAO classes, models, Maven setup, and login session. It does not add or change the database schema.

## Workflow and status rules

An administrator first moves a complaint from SUBMITTED to UNDER_REVIEW. They can then choose a department and add a required remark. Assignment changes the department and status to ASSIGNED and records the event in issue_history in one transaction.

| Current status | Allowed next status |
| --- | --- |
| SUBMITTED | UNDER_REVIEW, REJECTED |
| UNDER_REVIEW | ASSIGNED, REJECTED |
| ASSIGNED | IN_PROGRESS, REJECTED |
| IN_PROGRESS | RESOLVED, REJECTED |
| RESOLVED | none |
| REJECTED | none |

ASSIGNED must be reached through the department assignment action. Reassigning an already assigned complaint records an ASSIGNED -> ASSIGNED history event with the administrator's remark. Status updates and assignment require a non-empty remark, a valid administrator user ID, and a valid transition.

## Files

- `src/main/java/com/publicissues/model/IssueStatus.java`: enum and transition rules.
- `src/main/java/com/publicissues/service/IssueManagementService.java`: transaction handling, role checks, transition validation, assignment, and history writes.
- `src/main/java/com/publicissues/dao/IssueDAO.java`: adds a row-locking query used during changes.
- `src/main/java/com/publicissues/ui/AssignmentPanel.java`: Swing panel for admin assignment and status updates.
- `src/test/java/com/publicissues/model/IssueStatusTest.java`: transition unit tests.
- `src/test/java/com/publicissues/service/IssueBackendIntegrationTest.java`: assignment, history, authorization, rollback, and invalid-transition checks.

## Display the panel

After login, the admin UI can display the panel with:

```java
if (SessionManager.isAdmin()) {
    frame.setContentPane(new AssignmentPanel());
}
```

The panel also checks that an admin session exists, and the service independently checks the administrator role in the database for every write.

## Run tests safely

The Maven integration test is configured for the isolated `civic_issue_test_db` database. Follow [TEST_DATABASE.md](../src/test/resources/TEST_DATABASE.md) and use only its synthetic test users and departments. Never run the test setup script against `civic_issue_db`.

After configuring the isolated test MySQL database and the environment variables described there, run:

```powershell
mvn test
```

If that test database is unavailable, the integration tests are not verified; do not substitute production credentials.
