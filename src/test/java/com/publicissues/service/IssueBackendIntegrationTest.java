package com.publicissues.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.publicissues.DBConnection;
import com.publicissues.dao.DepartmentDAO;
import com.publicissues.dao.IssueDAO;
import com.publicissues.dao.IssueHistoryDAO;
import com.publicissues.dao.UserDAO;
import com.publicissues.model.Department;
import com.publicissues.model.Issue;
import com.publicissues.model.User;

class IssueBackendIntegrationTest {

    private static final String CITIZEN_EMAIL = "test.citizen@civic-issue.invalid";
    private static final String ADMIN_EMAIL = "test.admin@civic-issue.invalid";
    private static final String TEST_DEPARTMENT = "TEST - Public Works";

    private static final IssueDAO ISSUE_DAO = new IssueDAO();
    private static final IssueHistoryDAO HISTORY_DAO = new IssueHistoryDAO();
    private static final UserDAO USER_DAO = new UserDAO();
    private static final DepartmentDAO DEPARTMENT_DAO = new DepartmentDAO();
    private static final IssueService ISSUE_SERVICE = new IssueService();
    private static final IssueManagementService MANAGEMENT_SERVICE =
            new IssueManagementService();

    private final List<Integer> createdIssueIds = new ArrayList<>();

    @BeforeAll
    static void requireTestDatabaseProfile() throws SQLException {
        if (!Boolean.getBoolean("publicissues.testDatabase")) {
            throw new IllegalStateException(
                    "Integration tests require the isolated test database profile.");
        }
        try (Connection connection = DBConnection.getConnection()) {
            assertEquals("civic_issue_test_db", connection.getCatalog());
        }
    }

    @AfterEach
    void removeOnlyIssuesCreatedByThisTest() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            if (!"civic_issue_test_db".equals(connection.getCatalog())) {
                throw new SQLException("Refusing test cleanup outside the test database.");
            }
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement history = connection.prepareStatement(
                        "DELETE FROM issue_history WHERE issue_id = ?");
                     PreparedStatement issue = connection.prepareStatement(
                             "DELETE FROM issues WHERE issue_id = ?")) {
                    for (int issueId : createdIssueIds) {
                        history.setInt(1, issueId);
                        history.executeUpdate();
                        issue.setInt(1, issueId);
                        issue.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        } finally {
            createdIssueIds.clear();
        }
    }

    @Test
    void databaseConnectionTargetsOnlyTheTestCatalog() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            assertEquals("civic_issue_test_db", connection.getCatalog());
        }
    }

    @Test
    void submissionGeneratesThirtyCharacterTrackingCode() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);

        assertEquals(issueId, issue.getIssueId());
        assertEquals("SUBMITTED", issue.getStatus());
        assertNotNull(issue.getTrackingCode());
        assertEquals(30, issue.getTrackingCode().length());
        assertTrue(issue.getTrackingCode().matches("ISS-[0-9A-F]{26}"));
    }

    @Test
    void trackingCodeRetrievesTheSubmittedIssue() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);

        Issue retrieved = ISSUE_SERVICE.trackIssue(issue.getTrackingCode());

        assertNotNull(retrieved);
        assertEquals(issueId, retrieved.getIssueId());
        assertEquals(issue.getTrackingCode(), retrieved.getTrackingCode());
    }

    @Test
    void citizenCannotPerformAdminStatusChange() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);
        int citizenId = requireUser(CITIZEN_EMAIL).getUserId();

        assertThrows(IllegalArgumentException.class,
                () -> MANAGEMENT_SERVICE.updateStatus(
                        issueId, citizenId, "UNDER_REVIEW", "unauthorized test"));
        assertEquals("SUBMITTED", loadIssue(issueId).getStatus());
    }

    @Test
    void adminStatusChangeWritesMatchingHistory() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();

        MANAGEMENT_SERVICE.updateStatus(
                issueId, adminId, "UNDER_REVIEW", "integration status test");

        assertEquals("UNDER_REVIEW", loadIssue(issueId).getStatus());
        List<String> history = HISTORY_DAO.findHistoryByIssueId(issueId);
        assertEquals(1, history.size());
        assertTrue(history.get(0).contains("SUBMITTED -> UNDER_REVIEW"));
    }

    @Test
    void departmentAssignmentSetsDepartmentAndAssignedStatus() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();
        int departmentId = requireDepartmentId();

        MANAGEMENT_SERVICE.assignDepartment(
                issueId, departmentId, adminId, "integration assignment test");

        Issue assigned = loadIssue(issueId);
        assertEquals(departmentId, assigned.getDepartmentId());
        assertEquals("ASSIGNED", assigned.getStatus());
        assertEquals(1, HISTORY_DAO.findHistoryByIssueId(issueId).size());
    }

    @Test
    void failedHistoryInsertRollsBackStatusChange() throws SQLException {
        Issue issue = newIssue();
        int issueId = ISSUE_SERVICE.submitIssue(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();

        IssueHistoryDAO failingHistoryDAO = new IssueHistoryDAO() {
            @Override
            public void addHistory(Connection connection, int historyIssueId,
                                   int changedBy, String oldStatus,
                                   String newStatus, String remarks)
                    throws SQLException {
                throw new SQLException("Injected history insertion failure.");
            }
        };
        IssueManagementService service = new IssueManagementService(
                ISSUE_DAO, failingHistoryDAO, DEPARTMENT_DAO, USER_DAO);

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus(
                        issueId, adminId, "UNDER_REVIEW", "rollback test"));

        assertEquals("SUBMITTED", loadIssue(issueId).getStatus());
        assertTrue(HISTORY_DAO.findHistoryByIssueId(issueId).isEmpty());
    }

    private Issue newIssue() throws SQLException {
        User citizen = requireUser(CITIZEN_EMAIL);
        Issue issue = new Issue();
        issue.setCitizenId(citizen.getUserId());
        issue.setTitle("Isolated backend test issue");
        issue.setCategory("TEST");
        issue.setDescription("Synthetic issue created only in civic_issue_test_db.");
        issue.setLocation("Test fixture location");
        return issue;
    }

    private User requireUser(String email) throws SQLException {
        User user = USER_DAO.findByEmail(email);
        if (user == null) {
            throw new IllegalStateException("Required synthetic test user is missing.");
        }
        return user;
    }

    private int requireDepartmentId() throws SQLException {
        for (Department department : DEPARTMENT_DAO.findAll()) {
            if (TEST_DEPARTMENT.equals(department.getDepartmentName())) {
                return department.getDepartmentId();
            }
        }
        throw new IllegalStateException("Synthetic test department is missing.");
    }

    private Issue loadIssue(int issueId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return ISSUE_DAO.findById(connection, issueId);
        }
    }
}
