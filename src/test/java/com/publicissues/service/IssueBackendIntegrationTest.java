package com.publicissues.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.mindrot.jbcrypt.BCrypt;

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
    private static final AuthService AUTH_SERVICE = new AuthService();

    private final List<Integer> createdIssueIds = new ArrayList<>();
    private final List<Integer> createdUserIds = new ArrayList<>();

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
        try {
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
                    try (PreparedStatement user = connection.prepareStatement(
                            "DELETE FROM app_users WHERE user_id = ?")) {
                        for (int userId : createdUserIds) {
                            user.setInt(1, userId);
                            user.executeUpdate();
                        }
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
            }
        } finally {
            SessionManager.logout();
            createdIssueIds.clear();
            createdUserIds.clear();
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
        int issueId = submitAsCitizen(issue);
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
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);

        var retrieved = ISSUE_SERVICE.trackPublicIssue(issue.getTrackingCode());

        assertNotNull(retrieved);
        assertEquals(issue.getTrackingCode(), retrieved.trackingCode());
    }

    @Test
    void citizenCannotPerformAdminStatusChange() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
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
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();
        SessionManager.startSession(requireUser(ADMIN_EMAIL));

        MANAGEMENT_SERVICE.updateStatus(
                issueId, adminId, "UNDER_REVIEW", "integration status test");

        assertEquals("UNDER_REVIEW", loadIssue(issueId).getStatus());
        List<String> history = HISTORY_DAO.findHistoryByIssueId(issueId);
        assertEquals(1, history.size());
        assertTrue(history.get(0).contains("SUBMITTED -> UNDER_REVIEW"));
        assertEquals(adminId, historyChangedBy(issueId));
    }

    @Test
    void departmentAssignmentSetsDepartmentAndAssignedStatus() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();
        SessionManager.startSession(requireUser(ADMIN_EMAIL));
        int departmentId = requireDepartmentId();

        MANAGEMENT_SERVICE.assignDepartment(
                issueId, departmentId, adminId, "integration assignment test");

        Issue assigned = loadIssue(issueId);
        assertEquals(departmentId, assigned.getDepartmentId());
        assertEquals("ASSIGNED", assigned.getStatus());
        assertEquals(1, HISTORY_DAO.findHistoryByIssueId(issueId).size());
        assertEquals(adminId, historyChangedBy(issueId));
    }

    @Test
    void failedHistoryInsertRollsBackStatusChange() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        int adminId = requireUser(ADMIN_EMAIL).getUserId();
        SessionManager.startSession(requireUser(ADMIN_EMAIL));

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

    @Test
    void failedHistoryInsertRollsBackDepartmentAssignment() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        User admin = requireUser(ADMIN_EMAIL);
        SessionManager.startSession(admin);
        int departmentId = requireDepartmentId();
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

        assertThrows(IllegalStateException.class, () -> service.assignDepartment(
                issueId, departmentId, admin.getUserId(), "rollback assignment"));
        Issue unchanged = loadIssue(issueId);
        assertNull(unchanged.getDepartmentId());
        assertEquals("SUBMITTED", unchanged.getStatus());
        assertTrue(HISTORY_DAO.findHistoryByIssueId(issueId).isEmpty());
    }

    @Test
    void adminMutationsRequireMatchingAuthenticatedAdmin() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        User adminA = requireUser(ADMIN_EMAIL);
        User adminB = createScopedUser("ADMIN");

        SessionManager.logout();
        assertThrows(IllegalArgumentException.class, () ->
                MANAGEMENT_SERVICE.updateStatus(issueId, adminA.getUserId(),
                        "UNDER_REVIEW", "unauthenticated"));
        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        assertThrows(IllegalArgumentException.class, () ->
                MANAGEMENT_SERVICE.updateStatus(issueId, adminA.getUserId(),
                        "UNDER_REVIEW", "citizen spoof"));
        SessionManager.startSession(adminA);
        assertThrows(IllegalArgumentException.class, () ->
                MANAGEMENT_SERVICE.updateStatus(issueId, adminB.getUserId(),
                        "UNDER_REVIEW", "admin spoof"));
        assertThrows(IllegalArgumentException.class, () ->
                MANAGEMENT_SERVICE.assignDepartment(issueId, requireDepartmentId(),
                        adminB.getUserId(), "admin spoof assignment"));
        assertEquals("SUBMITTED", loadIssue(issueId).getStatus());
        assertNull(loadIssue(issueId).getDepartmentId());
    }

    @Test
    void allIssueListRequiresAdministratorSession() throws SQLException {
        SessionManager.logout();
        assertThrows(IllegalArgumentException.class, ISSUE_SERVICE::getAllIssues);
        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        assertThrows(IllegalArgumentException.class, ISSUE_SERVICE::getAllIssues);
        SessionManager.startSession(requireUser(ADMIN_EMAIL));
        assertNotNull(ISSUE_SERVICE.getAllIssues());
    }

    @Test
    void publicTrackingReturnsAllowlistedFieldsWithoutLogin() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        SessionManager.logout();
        var publicView = ISSUE_SERVICE.trackPublicIssue(issue.getTrackingCode());
        assertNotNull(publicView);
        assertEquals(issue.getTrackingCode(), publicView.trackingCode());
        assertEquals("SUBMITTED", publicView.status());
        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.trackPublicIssue("ISS-INVALID-NO-MATCH"));
        assertNull(ISSUE_SERVICE.trackPublicIssue("ISS-00000000000000000000000000"));
        assertEquals(List.of("trackingCode", "title", "category", "status", "submittedAt"),
                java.util.Arrays.stream(publicView.getClass().getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void issueDetailsAndHistoryRequireOwnerOrAdministrator() throws SQLException {
        Issue issue = newIssue();
        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);
        SessionManager.logout();
        assertThrows(IllegalArgumentException.class, () -> ISSUE_SERVICE.getIssueDetails(issueId));
        assertThrows(IllegalArgumentException.class, () -> ISSUE_SERVICE.getIssueHistory(issueId));

        User otherCitizen = createScopedUser("CITIZEN");
        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        assertEquals(issueId, ISSUE_SERVICE.getIssueDetails(issueId).getIssueId());
        SessionManager.startSession(otherCitizen);
        IllegalArgumentException unauthorized = assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.getIssueDetails(issueId));
        IllegalArgumentException nonexistent = assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.getIssueDetails(Integer.MAX_VALUE));
        assertEquals(unauthorized.getClass(), nonexistent.getClass());
        assertEquals(unauthorized.getMessage(), nonexistent.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ISSUE_SERVICE.getIssueHistory(issueId));

        User admin = requireUser(ADMIN_EMAIL);
        SessionManager.startSession(admin);
        MANAGEMENT_SERVICE.updateStatus(issueId, admin.getUserId(), "UNDER_REVIEW", "history test");
        assertNotNull(ISSUE_SERVICE.getIssueDetails(issueId));
        List<String> authorizedHistory = ISSUE_SERVICE.getIssueHistory(issueId);
        assertEquals(1, authorizedHistory.size());
        assertTrue(authorizedHistory.get(0).contains("Updated by: " + admin.getName()));

        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        assertEquals(issueId, ISSUE_SERVICE.getIssueDetails(issueId).getIssueId());
        assertNull(ISSUE_SERVICE.getIssueDetails(issueId).getCitizenEmail());
        assertEquals(authorizedHistory, ISSUE_SERVICE.getIssueHistory(issueId));

        SessionManager.startSession(admin);
        Issue adminView = ISSUE_SERVICE.getIssueDetails(issueId);
        assertEquals("Test Citizen", adminView.getCitizenName());
        assertEquals(CITIZEN_EMAIL, adminView.getCitizenEmail());
    }

    @Test
    void invalidStatusValueIsRejected() throws SQLException {
        User admin = requireUser(ADMIN_EMAIL);
        SessionManager.startSession(admin);
        assertThrows(IllegalArgumentException.class, () ->
                MANAGEMENT_SERVICE.updateStatus(1, admin.getUserId(), "CLOSED", "invalid"));
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndRejectsDuplicate()
            throws SQLException {
        String email = "Sprint.Auth." + java.util.UUID.randomUUID()
                + "@civic-issue.invalid";
        User registered = AUTH_SERVICE.registerCitizen(
                "  Sprint Citizen  ", " " + email + " ", null, "SafeTestPass123");
        createdUserIds.add(registered.getUserId());

        assertEquals(email.toLowerCase(java.util.Locale.ROOT), registered.getEmail());
        assertEquals("Sprint Citizen", registered.getName());
        assertNull(registered.getPasswordHash());
        User stored = USER_DAO.findById(registered.getUserId());
        assertNotNull(stored.getPasswordHash());
        assertTrue(BCrypt.checkpw("SafeTestPass123", stored.getPasswordHash()));
        assertThrows(IllegalArgumentException.class, () -> AUTH_SERVICE.registerCitizen(
                "Duplicate", email.toUpperCase(java.util.Locale.ROOT), null,
                "SafeTestPass123"));
    }

    @Test
    void loginValidatesPasswordRoleAndSessionLogout() throws SQLException {
        User registered = registerTestCitizen();
        User authenticated = AUTH_SERVICE.loginAsCitizen(
                registered.getEmail().toUpperCase(java.util.Locale.ROOT),
                "SafeTestPass123");
        assertEquals(registered.getUserId(), authenticated.getUserId());
        assertNull(authenticated.getPasswordHash());
        assertThrows(IllegalArgumentException.class, () -> AUTH_SERVICE.loginAsCitizen(
                registered.getEmail(), "WrongTestPass123"));
        assertThrows(IllegalArgumentException.class, () -> AUTH_SERVICE.loginAsAdmin(
                registered.getEmail(), "SafeTestPass123"));

        SessionManager.startSession(authenticated);
        assertTrue(SessionManager.isLoggedIn());
        SessionManager.logout();
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void unauthenticatedUserCannotSubmitComplaint() throws SQLException {
        SessionManager.logout();

        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.submitIssue(newIssue()));
    }

    @Test
    void administratorCannotSubmitCitizenComplaint() throws SQLException {
        SessionManager.startSession(requireUser(ADMIN_EMAIL));

        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.submitIssue(newIssue()));
    }

    @Test
    void citizenCanSubmitComplaint() throws SQLException {
        Issue issue = newIssue();

        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);

        assertEquals(issueId, issue.getIssueId());
        assertEquals("SUBMITTED", issue.getStatus());
    }

    @Test
    void citizenCannotSubmitComplaintWithAnotherSuppliedCitizenId()
            throws SQLException {
        User citizen = requireUser(CITIZEN_EMAIL);
        Issue issue = newIssue();
        issue.setCitizenId(requireUser(ADMIN_EMAIL).getUserId());

        int issueId = submitAsCitizen(issue);
        createdIssueIds.add(issueId);

        assertEquals(citizen.getUserId(), issue.getCitizenId());
        assertEquals(citizen.getUserId(), loadIssue(issueId).getCitizenId());
    }

    @Test
    void citizenSubmissionValidatesSchemaFieldLengths() throws SQLException {
        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        Issue issue = newIssue();
        issue.setTitle("T".repeat(151));
        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.submitIssue(issue));

        issue.setTitle("Valid title");
        issue.setCategory("C".repeat(101));
        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.submitIssue(issue));

        issue.setCategory("Valid category");
        issue.setLocation("L".repeat(256));
        assertThrows(IllegalArgumentException.class,
                () -> ISSUE_SERVICE.submitIssue(issue));
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

    private int submitAsCitizen(Issue issue) throws SQLException {
        SessionManager.startSession(requireUser(CITIZEN_EMAIL));
        return ISSUE_SERVICE.submitIssue(issue);
    }

    private User createScopedUser(String role) throws SQLException {
        String email = "sprint." + role.toLowerCase() + "."
                + java.util.UUID.randomUUID() + "@civic-issue.invalid";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO app_users (name,email,password_hash,role) VALUES (?,?,?,?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Sprint Test " + role);
            statement.setString(2, email);
            statement.setString(3, "TEST_FIXTURE_NO_PASSWORD_AUTHENTICATION");
            statement.setString(4, role);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Test user ID was not generated.");
                int id = keys.getInt(1);
                createdUserIds.add(id);
                return USER_DAO.findById(id);
            }
        }
    }

    private User registerTestCitizen() {
        String email = "sprint.login." + java.util.UUID.randomUUID()
                + "@civic-issue.invalid";
        User user = AUTH_SERVICE.registerCitizen(
                "Login Test Citizen", email, null, "SafeTestPass123");
        createdUserIds.add(user.getUserId());
        return user;
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

    private int historyChangedBy(int issueId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT changed_by FROM issue_history WHERE issue_id = ? ORDER BY history_id DESC LIMIT 1")) {
            statement.setInt(1, issueId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Expected complaint history row is missing.");
                return result.getInt("changed_by");
            }
        }
    }
}
