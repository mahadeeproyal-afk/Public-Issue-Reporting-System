package com.publicissues.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.publicissues.DBConnection;
import com.publicissues.dao.DepartmentDAO;
import com.publicissues.dao.IssueDAO;
import com.publicissues.dao.IssueHistoryDAO;
import com.publicissues.dao.UserDAO;
import com.publicissues.model.Department;
import com.publicissues.model.Issue;
import com.publicissues.model.IssueStatus;
import com.publicissues.model.User;

/** Business rules and transactional updates for administrator issue management. */
public class IssueManagementService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "SUBMITTED", "UNDER_REVIEW", "ASSIGNED",
            "IN_PROGRESS", "RESOLVED", "REJECTED");
    private static final int MAX_REMARK_LENGTH = 1000;

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

    public List<Issue> findIssuesForManagement() {
        try {
            return issueDAO.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load complaints.", e);
        }
    }

    public List<Department> findDepartments() {
        try {
            return departmentDAO.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load departments.", e);
        }
    }

    public void updateStatus(
            int issueId, int changedBy, String newStatus, String remarks) {
        validatePositiveId(issueId, "issue ID");
        validatePositiveId(changedBy, "admin user ID");
        IssueStatus targetStatus = parseStatus(newStatus);
        validateRemarks(remarks);
        if (targetStatus == IssueStatus.ASSIGNED) {
            throw new IllegalArgumentException(
                    "Choose a department and use Assign Department to set ASSIGNED.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdmin(connection, changedBy);
                Issue issue = requireIssueForUpdate(connection, issueId);
                IssueStatus oldStatus = parseStatus(issue.getStatus());
                if (!oldStatus.canTransitionTo(targetStatus)) {
                    throw new IllegalArgumentException(
                            "Status transition " + oldStatus + " -> "
                                    + targetStatus + " is not allowed.");
                }
                if (!issueDAO.updateStatus(
                        connection, issueId, targetStatus.name())) {
                    throw new SQLException("Issue status was not updated.");
                }
                issueHistoryDAO.addHistory(connection, issueId, changedBy,
                        oldStatus.name(), targetStatus.name(), remarks.trim());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update issue status.", e);
        }
    }

    /** Assign from UNDER_REVIEW, or record a department reassignment. */
    public void assignDepartment(
            int issueId, int departmentId, int changedBy, String remarks) {
        validatePositiveId(issueId, "issue ID");
        validatePositiveId(departmentId, "department ID");
        validatePositiveId(changedBy, "admin user ID");
        validateRemarks(remarks);

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAdmin(connection, changedBy);
                Issue issue = requireIssueForUpdate(connection, issueId);
                IssueStatus oldStatus = parseStatus(issue.getStatus());
                if (!departmentDAO.exists(connection, departmentId)) {
                    throw new IllegalArgumentException("Department not found.");
                }

                boolean isReassignment = oldStatus == IssueStatus.ASSIGNED;
                if (!oldStatus.canTransitionTo(IssueStatus.ASSIGNED)
                        && !isReassignment) {
                    throw new IllegalArgumentException(
                            "Move the complaint to UNDER_REVIEW before assigning it.");
                }
                if (Objects.equals(issue.getDepartmentId(), departmentId)) {
                    throw new IllegalArgumentException(
                            "The complaint is already assigned to that department.");
                }

                if (!issueDAO.assignDepartment(
                        connection, issueId, departmentId)) {
                    throw new SQLException("Department assignment failed.");
                }
                if (!isReassignment
                        && !issueDAO.updateStatus(
                                connection, issueId, IssueStatus.ASSIGNED.name())) {
                    throw new SQLException(
                            "Issue status was not updated to ASSIGNED.");
                }
                issueHistoryDAO.addHistory(connection, issueId, changedBy,
                        oldStatus.name(), IssueStatus.ASSIGNED.name(),
                        remarks.trim());
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

    private Issue requireIssueForUpdate(Connection connection, int issueId)
            throws SQLException {
        Issue issue = issueDAO.findByIdForUpdate(connection, issueId);
        if (issue == null) throw new IllegalArgumentException("Issue not found.");
        return issue;
    }

    private IssueStatus parseStatus(String status) {
        if (status == null || !VALID_STATUSES.contains(
                status.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid issue status.");
        }
        return IssueStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    }

    private void validatePositiveId(int id, String name) {
        if (id <= 0) throw new IllegalArgumentException("Invalid " + name + ".");
    }

    private void validateRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            throw new IllegalArgumentException("Please enter a remark.");
        }
        if (remarks.trim().length() > MAX_REMARK_LENGTH) {
            throw new IllegalArgumentException(
                    "Remarks must be 1000 characters or fewer.");
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
