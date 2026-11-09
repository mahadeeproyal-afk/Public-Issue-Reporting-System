package com.publicissues.service;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;

import com.publicissues.dao.IssueDAO;
import com.publicissues.dao.IssueHistoryDAO;
import com.publicissues.dao.UserDAO;
import com.publicissues.model.Issue;
import com.publicissues.model.PublicIssueTracking;
import com.publicissues.model.User;

public class IssueService {

    private static final int TITLE_MAX_LENGTH = 150;
    private static final int CATEGORY_MAX_LENGTH = 100;
    private static final int LOCATION_MAX_LENGTH = 255;
    private static final int DESCRIPTION_MAX_BYTES = 65_535;
    private static final Pattern TRACKING_CODE_PATTERN =
            Pattern.compile("ISS-[0-9A-F]{26}");

    private final IssueDAO issueDAO;
    private final IssueHistoryDAO historyDAO;

    public IssueService() {
        this.issueDAO = new IssueDAO();
        this.historyDAO = new IssueHistoryDAO();
    }

    // Submit a new public issue
    public int submitIssue(Issue issue) {

        if (!SessionManager.isCitizen()) {
            throw new IllegalArgumentException(
                    "An authenticated citizen session is required.");
        }

        int authenticatedCitizenId = SessionManager.getCurrentUserId();

        if (issue == null) {
            throw new IllegalArgumentException("Issue cannot be empty.");
        }

        if (issue.getTitle() == null || issue.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Issue title is required.");
        }

        if (issue.getDescription() == null || issue.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Issue description is required.");
        }

        if (issue.getLocation() == null || issue.getLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("Issue location is required.");
        }

        if (issue.getCategory() == null || issue.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Issue category is required.");
        }

        String title = issue.getTitle().trim();
        String category = issue.getCategory().trim();
        String location = issue.getLocation().trim();
        String description = issue.getDescription().trim();
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Title must be 150 characters or fewer.");
        }
        if (category.length() > CATEGORY_MAX_LENGTH) {
            throw new IllegalArgumentException("Category must be 100 characters or fewer.");
        }
        if (location.length() > LOCATION_MAX_LENGTH) {
            throw new IllegalArgumentException("Location must be 255 characters or fewer.");
        }
        if (description.getBytes(StandardCharsets.UTF_8).length > DESCRIPTION_MAX_BYTES) {
            throw new IllegalArgumentException("Description is too long. Keep it under 65,535 UTF-8 bytes.");
        }

        issue.setTitle(title);
        issue.setCategory(category);
        issue.setLocation(location);
        issue.setDescription(description);
        issue.setCitizenId(authenticatedCitizenId);
        issue.setStatus("SUBMITTED");
        issue.setTrackingCode(generateTrackingCode());

        try {
            return issueDAO.createIssue(issue);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to submit issue.", e);
        }
    }

    private String generateTrackingCode() {
        String hexadecimal = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 26)
                .toUpperCase(Locale.ROOT);
        return "ISS-" + hexadecimal;
    }

    /** Minimal public tracking view. This intentionally omits description, location, owner and history. */
    public PublicIssueTracking trackPublicIssue(String trackingCode) {

        if (trackingCode == null || trackingCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking code is required.");
        }

        String normalizedCode = trackingCode.trim().toUpperCase(Locale.ROOT);
        if (!TRACKING_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("Tracking code format is invalid.");
        }

        try {
            return issueDAO.findPublicTrackingByCode(normalizedCode);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to track issue.", e);
        }
    }

    // Get all issues submitted by a particular citizen
    public List<Issue> getCitizenIssues(int citizenId) {

        if (citizenId <= 0) {
            throw new IllegalArgumentException("Invalid citizen ID.");
        }
        if (!SessionManager.isCitizen()
                || SessionManager.getCurrentUserId() != citizenId) {
            throw new IllegalArgumentException(
                    "You may only view your own complaints.");
        }

        try {
            return issueDAO.findByCitizenId(citizenId);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve citizen issues.", e);
        }
    }

    // Get all public issues
    public List<Issue> getAllIssues() {

        if (!SessionManager.isAdmin()) {
            throw new IllegalArgumentException(
                    "An authenticated administrator session is required.");
        }
        int actorId = SessionManager.getCurrentUserId();

        try {
            User activeUser = new UserDAO().findById(actorId);
            if (activeUser == null || !"ADMIN".equals(activeUser.getRole())) {
                throw new IllegalArgumentException("Administrator account is not active.");
            }
            return issueDAO.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve issues.", e);
        }
    }

    /** Returns full details only to the complaint owner or an active administrator. */
    public Issue getIssueDetails(int issueId) {
        if (issueId <= 0 || !SessionManager.isLoggedIn()) {
            throw new IllegalArgumentException("An authenticated session and valid complaint are required.");
        }
        try {
            if (SessionManager.isAdmin()) {
                User actor = new UserDAO().findById(SessionManager.getCurrentUserId());
                if (actor != null && "ADMIN".equals(actor.getRole())) {
                    return issueDAO.findByIdForAdmin(issueId);
                }
                throw new IllegalArgumentException("Administrator account is not active.");
            }
            if (!SessionManager.isCitizen()) {
                throw new IllegalArgumentException("An authenticated citizen or administrator session is required.");
            }
            Issue issue = issueDAO.findById(issueId);
            if (issue == null || issue.getCitizenId() != SessionManager.getCurrentUserId()) {
                throw unavailableComplaint();
            }
            return issue;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve complaint details.", e);
        }
    }

    private IllegalArgumentException unavailableComplaint() {
        return new IllegalArgumentException("Complaint not found or unavailable.");
    }

    /** History access is tied to a database verified owner/admin detail lookup. */
    public List<String> getIssueHistory(int issueId) {
        getIssueDetails(issueId);
        try {
            return historyDAO.findHistoryByIssueId(issueId);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve complaint history.", e);
        }
    }
}
