package com.publicissues.service;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

import com.publicissues.dao.IssueDAO;
import com.publicissues.model.Issue;

public class IssueService {

    private final IssueDAO issueDAO;

    public IssueService() {
        this.issueDAO = new IssueDAO();
    }

    // Submit a new public issue
    public int submitIssue(Issue issue) {

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

        if (issue.getCitizenId() <= 0) {
            throw new IllegalArgumentException("Invalid citizen ID.");
        }

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

    // Track an issue using its tracking code
    public Issue trackIssue(String trackingCode) {

        if (trackingCode == null || trackingCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking code is required.");
        }

        try {
            return issueDAO.findByTrackingCode(trackingCode.trim());
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to track issue.", e);
        }
    }

    // Get all issues submitted by a particular citizen
    public List<Issue> getCitizenIssues(int citizenId) {

        if (citizenId <= 0) {
            throw new IllegalArgumentException("Invalid citizen ID.");
        }

        try {
            return issueDAO.findByCitizenId(citizenId);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve citizen issues.", e);
        }
    }

    // Get all public issues
    public List<Issue> getAllIssues() {

        try {
            return issueDAO.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve issues.", e);
        }
    }
}
