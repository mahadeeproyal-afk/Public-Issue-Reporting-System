package com.publicissues.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import com.publicissues.DBConnection;
import com.publicissues.dao.DepartmentDAO;
import com.publicissues.dao.IssueDAO;
import com.publicissues.dao.IssueHistoryDAO;
import com.publicissues.dao.UserDAO;
import com.publicissues.model.Issue;
import com.publicissues.model.User;

public class IssueManagementService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "SUBMITTED", "UNDER_REVIEW", "ASSIGNED",
            "IN_PROGRESS", "RESOLVED", "REJECTED");

    private final IssueDAO issueDAO;
    private final IssueHistoryDAO issueHistoryDAO;
    private final DepartmentDAO departmentDAO;
    private final UserDAO userDAO;

    public IssueManagementService() {
        this.issueDAO = new IssueDAO();
        this.issueHistoryDAO = new IssueHistoryDAO();
        this.departmentDAO = new DepartmentDAO();
        this.userDAO = new UserDAO();
    }

    IssueManagementService(IssueDAO issueDAO,
                          IssueHistoryDAO issueHistoryDAO,
                          DepartmentDAO departmentDAO,
                          UserDAO userDAO) {
        this.issueDAO = issueDAO;
        this.issueHistoryDAO = issueHistoryDAO;
        this.departmentDAO = departmentDAO;
        this.userDAO = userDAO;
    }

    public void updateStatus(
            int issueId, int changedBy, String newStatus, String remarks) {
        validatePositiveId(issueId, "issue ID");
        validatePositiveId(changedBy, "admin user ID");
        validateStatus(newStatus);

        try (Connection connection = DBConnection.getConnection()) {
            beginTransaction(connection);
            try {
                requireAdmin(connection, changedBy);
                Issue issue = requireIssue(connection, issueId);
                String oldStatus = issue.getStatus();

                if (newStatus.equals(oldStatus)) {
                    throw new IllegalArgumentException(
                            "Issue already has the requested status.");
                }

                if (!issueDAO.updateStatus(connection, issueId, newStatus)) {
                    throw new SQLException("Issue status was not updated.");
                }

                issueHistoryDAO.addHistory(connection, issueId, changedBy,
                        oldStatus, newStatus, remarks);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update issue status.", e);
        }
    }

    public void assignDepartment(
            int issueId, int departmentId, int changedBy, String remarks) {
        validatePositiveId(issueId, "issue ID");
        validatePositiveId(departmentId, "department ID");
        validatePositiveId(changedBy, "admin user ID");

        try (Connection connection = DBConnection.getConnection()) {
            beginTransaction(connection);
            try {
                requireAdmin(connection, changedBy);
                Issue issue = requireIssue(connection, issueId);

                if (!departmentDAO.exists(connection, departmentId)) {
                    throw new IllegalArgumentException("Department not found.");
                }

                boolean departmentChanged = issue.getDepartmentId() == null
                        || issue.getDepartmentId() != departmentId;
                boolean statusNeedsAssignment = !"ASSIGNED".equals(issue.getStatus());

                if (departmentChanged
                        && !issueDAO.assignDepartment(
                                connection, issueId, departmentId)) {
                    throw new SQLException("Department assignment failed.");
                }

                if (statusNeedsAssignment) {
                    if (!issueDAO.updateStatus(
                            connection, issueId, "ASSIGNED")) {
                        throw new SQLException(
                                "Issue status was not updated to ASSIGNED.");
                    }
                    issueHistoryDAO.addHistory(connection, issueId, changedBy,
                            issue.getStatus(), "ASSIGNED", remarks);
                }

                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to assign issue.", e);
        }
    }

    private void requireAdmin(Connection connection, int userId)
            throws SQLException {
        User user = userDAO.findById(connection, userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException(
                    "Acting user does not exist or is not an administrator.");
        }
    }

    private Issue requireIssue(Connection connection, int issueId)
            throws SQLException {
        Issue issue = issueDAO.findById(connection, issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found.");
        }
        return issue;
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank()
                || !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid issue status.");
        }
    }

    private void validatePositiveId(int id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid " + name + ".");
        }
    }

    private void beginTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
