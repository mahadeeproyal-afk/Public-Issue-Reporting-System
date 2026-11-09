package com.publicissues.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.publicissues.DBConnection;
import com.publicissues.model.Issue;
import com.publicissues.model.PublicIssueTracking;

public class IssueDAO {

    // Submit a new complaint.
    public int createIssue(Issue issue) throws SQLException {

        String sql = """
                INSERT INTO issues
                (tracking_code, citizen_id, department_id,
                 title, category, description, location, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, issue.getTrackingCode());
            statement.setInt(2, issue.getCitizenId());

            if (issue.getDepartmentId() == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, issue.getDepartmentId());
            }

            statement.setString(4, issue.getTitle());
            statement.setString(5, issue.getCategory());
            statement.setString(6, issue.getDescription());
            statement.setString(7, issue.getLocation());
            statement.setString(8, issue.getStatus());

            int rows = statement.executeUpdate();

            if (rows == 0) {
                throw new SQLException("Complaint submission failed.");
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int issueId = keys.getInt(1);
                    issue.setIssueId(issueId);
                    return issueId;
                }
            }
        }

        throw new SQLException("Complaint ID could not be generated.");
    }

    /** Selects only the public tracking allowlist; never maps the private entity. */
    public PublicIssueTracking findPublicTrackingByCode(String trackingCode)
            throws SQLException {
        String sql = """
                SELECT tracking_code, title, category, status, created_at
                FROM issues WHERE tracking_code = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trackingCode);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Timestamp submitted = result.getTimestamp("created_at");
                return new PublicIssueTracking(result.getString("tracking_code"),
                        result.getString("title"), result.getString("category"),
                        result.getString("status"),
                        submitted == null ? null : submitted.toLocalDateTime());
            }
        }
    }

    /**
     * Raw persistence query used by IssueService.getAllIssues(), which requires
     * an active administrator session. Kept public for the service package.
     */
    public List<Issue> findAll() throws SQLException {

        String sql = """
                SELECT i.*, u.name AS citizen_name,
                       u.email AS citizen_email, u.phone AS citizen_phone
                FROM issues i
                JOIN app_users u ON u.user_id = i.citizen_id
                ORDER BY i.created_at DESC
                """;

        List<Issue> issues = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                Issue issue = mapIssue(result);
                mapCitizenContact(result, issue);
                issues.add(issue);
            }
        }

        return issues;
    }

    // Retrieve complaints belonging to a particular citizen.
    public List<Issue> findByCitizenId(int citizenId)
            throws SQLException {

        String sql = """
                SELECT * FROM issues
                WHERE citizen_id = ?
                ORDER BY created_at DESC
                """;

        List<Issue> issues = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, citizenId);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    issues.add(mapIssue(result));
                }
            }
        }

        return issues;
    }

    // Update the status using a new database connection.
    public boolean updateStatus(int issueId, String newStatus)
            throws SQLException {

        String sql = """
                UPDATE issues
                SET status = ?
                WHERE issue_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, newStatus);
            statement.setInt(2, issueId);

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Raw transaction query used by IssueManagementService and authorized
     * IssueService detail retrieval. Callers must enforce service-layer access.
     */
    public Issue findById(Connection connection, int issueId)
            throws SQLException {

        String sql = "SELECT * FROM issues WHERE issue_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, issueId);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return mapIssue(result);
                }
            }
        }

        return null;
    }

    /** Used by IssueService.getIssueDetails(), which verifies owner/admin access. */
    public Issue findById(int issueId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findById(connection, issueId);
        }
    }

    /** Admin-only contact enrichment; caller must verify an active admin session. */
    public Issue findByIdForAdmin(int issueId) throws SQLException {
        String sql = """
                SELECT i.*, u.name AS citizen_name,
                       u.email AS citizen_email, u.phone AS citizen_phone
                FROM issues i
                JOIN app_users u ON u.user_id = i.citizen_id
                WHERE i.issue_id = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, issueId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Issue issue = mapIssue(result);
                mapCitizenContact(result, issue);
                return issue;
            }
        }
    }

    // Update issue status using an existing transaction connection.
    public boolean updateStatus(Connection connection, int issueId,
                                String newStatus) throws SQLException {

        String sql = """
                UPDATE issues
                SET status = ?
                WHERE issue_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, newStatus);
            statement.setInt(2, issueId);

            return statement.executeUpdate() > 0;
        }
    }

    // Assign an issue to a department using an existing connection.
    public boolean assignDepartment(Connection connection, int issueId,
                                    int departmentId) throws SQLException {

        String sql = """
                UPDATE issues
                SET department_id = ?
                WHERE issue_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, departmentId);
            statement.setInt(2, issueId);

            return statement.executeUpdate() > 0;
        }
    }

    // Convert a database record into a Java Issue object.
    private Issue mapIssue(ResultSet result) throws SQLException {

        Issue issue = new Issue();

        issue.setIssueId(result.getInt("issue_id"));
        issue.setTrackingCode(result.getString("tracking_code"));
        issue.setCitizenId(result.getInt("citizen_id"));

        int departmentId = result.getInt("department_id");

        if (result.wasNull()) {
            issue.setDepartmentId(null);
        } else {
            issue.setDepartmentId(departmentId);
        }

        issue.setTitle(result.getString("title"));
        issue.setCategory(result.getString("category"));
        issue.setDescription(result.getString("description"));
        issue.setLocation(result.getString("location"));
        issue.setStatus(result.getString("status"));

        Timestamp createdAt = result.getTimestamp("created_at");

        if (createdAt != null) {
            issue.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = result.getTimestamp("updated_at");

        if (updatedAt != null) {
            issue.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return issue;
    }

    private void mapCitizenContact(ResultSet result, Issue issue)
            throws SQLException {
        issue.setCitizenName(result.getString("citizen_name"));
        issue.setCitizenEmail(result.getString("citizen_email"));
        issue.setCitizenPhone(result.getString("citizen_phone"));
    }
}
