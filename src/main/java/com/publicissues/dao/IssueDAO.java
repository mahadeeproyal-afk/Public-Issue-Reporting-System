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

public class IssueDAO {

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
            if (rows == 0) throw new SQLException("Complaint submission failed.");
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

    public Issue findByTrackingCode(String trackingCode) throws SQLException {
        String sql = "SELECT * FROM issues WHERE tracking_code = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trackingCode);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return mapIssue(result);
            }
        }
        return null;
    }

    public List<Issue> findAll() throws SQLException {
        String sql = "SELECT * FROM issues ORDER BY created_at DESC";
        List<Issue> issues = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) issues.add(mapIssue(result));
        }
        return issues;
    }

    public List<Issue> findByCitizenId(int citizenId) throws SQLException {
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
                while (result.next()) issues.add(mapIssue(result));
            }
        }
        return issues;
    }

    public boolean updateStatus(int issueId, String newStatus)
            throws SQLException {
        String sql = "UPDATE issues SET status = ? WHERE issue_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus);
            statement.setInt(2, issueId);
            return statement.executeUpdate() > 0;
        }
    }

    public Issue findById(Connection connection, int issueId)
            throws SQLException {
        return findById(connection, issueId, false);
    }

    /** Locks the row until the caller commits or rolls back its transaction. */
    public Issue findByIdForUpdate(Connection connection, int issueId)
            throws SQLException {
        return findById(connection, issueId, true);
    }

    private Issue findById(Connection connection, int issueId, boolean lock)
            throws SQLException {
        String sql = "SELECT * FROM issues WHERE issue_id = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, issueId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return mapIssue(result);
            }
        }
        return null;
    }

    public boolean updateStatus(Connection connection, int issueId,
                                String newStatus) throws SQLException {
        String sql = "UPDATE issues SET status = ? WHERE issue_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus);
            statement.setInt(2, issueId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean assignDepartment(Connection connection, int issueId,
                                    int departmentId) throws SQLException {
        String sql = "UPDATE issues SET department_id = ? WHERE issue_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, departmentId);
            statement.setInt(2, issueId);
            return statement.executeUpdate() > 0;
        }
    }

    private Issue mapIssue(ResultSet result) throws SQLException {
        Issue issue = new Issue();
        issue.setIssueId(result.getInt("issue_id"));
        issue.setTrackingCode(result.getString("tracking_code"));
        issue.setCitizenId(result.getInt("citizen_id"));
        int departmentId = result.getInt("department_id");
        issue.setDepartmentId(result.wasNull() ? null : departmentId);
        issue.setTitle(result.getString("title"));
        issue.setCategory(result.getString("category"));
        issue.setDescription(result.getString("description"));
        issue.setLocation(result.getString("location"));
        issue.setStatus(result.getString("status"));
        Timestamp createdAt = result.getTimestamp("created_at");
        if (createdAt != null) issue.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = result.getTimestamp("updated_at");
        if (updatedAt != null) issue.setUpdatedAt(updatedAt.toLocalDateTime());
        return issue;
    }
}
