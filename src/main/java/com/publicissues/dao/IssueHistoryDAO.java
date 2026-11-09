package com.publicissues.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.publicissues.DBConnection;

public class IssueHistoryDAO {

    // Record a complaint status change using a new connection.
    public void addHistory(int issueId, int changedBy,
                           String oldStatus, String newStatus,
                           String remarks) throws SQLException {

        String sql = """
                INSERT INTO issue_history
                (issue_id, changed_by, old_status, new_status, remarks)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, issueId);
            statement.setInt(2, changedBy);
            statement.setString(3, oldStatus);
            statement.setString(4, newStatus);
            statement.setString(5, remarks);

            statement.executeUpdate();
        }
    }

    // Record history using an existing transaction connection.
    public void addHistory(Connection connection,
                           int issueId,
                           int changedBy,
                           String oldStatus,
                           String newStatus,
                           String remarks) throws SQLException {

        String sql = """
                INSERT INTO issue_history
                (issue_id, changed_by, old_status, new_status, remarks)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, issueId);
            statement.setInt(2, changedBy);
            statement.setString(3, oldStatus);
            statement.setString(4, newStatus);
            statement.setString(5, remarks);

            statement.executeUpdate();
        }
    }

    // Retrieve the complete history of a complaint.
    public List<String> findHistoryByIssueId(int issueId)
            throws SQLException {

        String sql = """
                SELECT h.old_status, h.new_status, h.remarks, h.changed_at, u.name AS actor_name
                FROM issue_history h
                JOIN app_users u ON u.user_id = h.changed_by
                WHERE h.issue_id = ?
                ORDER BY h.changed_at ASC, h.history_id ASC
                """;

        List<String> history = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, issueId);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    String oldStatus = result.getString("old_status");
                    String newStatus = result.getString("new_status");
                    String remarks = result.getString("remarks");

                    Timestamp changedAt = result.getTimestamp("changed_at");
                    String actorName = result.getString("actor_name");

                    String entry = "Status: "
                            + (oldStatus == null ? "NEW" : oldStatus)
                            + " -> " + newStatus
                            + "\nRemarks: "
                            + (remarks == null || remarks.isBlank() ? "None" : remarks)
                            + "\nDate: " + changedAt
                            + "\nUpdated by: " + (actorName == null ? "Unavailable" : actorName);

                    history.add(entry);
                }
            }
        }

        return history;
    }
}
