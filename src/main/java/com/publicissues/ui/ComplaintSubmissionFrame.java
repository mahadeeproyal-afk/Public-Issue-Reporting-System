package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.publicissues.model.Issue;
import com.publicissues.model.User;
import com.publicissues.service.IssueService;
import com.publicissues.service.SessionManager;

/**
 * Complaint Submission screen (Module 03).
 * Citizen fills the form -> builds an Issue -> IssueService saves it
 * -> we show the tracking code.
 */
public class ComplaintSubmissionFrame extends JFrame {

    private static final String PLACEHOLDER = "-- Select category --";
    private static final String[] CATEGORIES = {
            PLACEHOLDER,
            "Roads & Potholes",
            "Garbage",
            "Water Leakage",
            "Electricity / Streetlight",
            "Drainage",
            "Other"
    };

    // Limits match the database columns (title 150, location 255).
    private static final int MAX_TITLE = 150;
    private static final int MAX_LOCATION = 255;
    private static final int MAX_DESCRIPTION = 2000;

    private final IssueService issueService = new IssueService();

    private final JComboBox<String> categoryBox = new JComboBox<>(CATEGORIES);
    private final JTextField titleField = new JTextField(30);
    private final JTextField locationField = new JTextField(30);
    private final JTextArea descriptionArea = new JTextArea(6, 30);

    public ComplaintSubmissionFrame() {
        setTitle("Report a Public Issue");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel heading = new JLabel("Report a Public Issue");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        heading.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JButton submitButton = new JButton("Submit Complaint");
        JButton clearButton = new JButton("Clear");
        submitButton.addActionListener(e -> handleSubmit());
        clearButton.addActionListener(e -> clearForm());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));
        addRow(form, 0, "Category *", categoryBox);
        addRow(form, 1, "Title *", titleField);
        addRow(form, 2, "Location *", locationField);
        addRow(form, 3, "Description *", new JScrollPane(descriptionArea));

        JPanel buttons = new JPanel();
        buttons.add(submitButton);
        buttons.add(clearButton);

        add(heading, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void addRow(JPanel panel, int row, String label,
                        java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridx = 0;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private void handleSubmit() {
        // 1. Must be a logged-in citizen.
        if (!SessionManager.isLoggedIn() || !SessionManager.isCitizen()) {
            showError("Please log in as a citizen to report an issue.");
            return;
        }

        // 2. Read the form.
        String category = (String) categoryBox.getSelectedItem();
        String title = titleField.getText().trim();
        String location = locationField.getText().trim();
        String description = descriptionArea.getText().trim();

        // 3. Validate.
        String error = validate(category, title, location, description);
        if (error != null) {
            showError(error);
            return;
        }

        // 4. Build the Issue and save it through the shared service.
        Issue issue = new Issue();
        issue.setCitizenId(SessionManager.getCurrentUserId());
        issue.setCategory(category);
        issue.setTitle(title);
        issue.setLocation(location);
        issue.setDescription(description);

        try {
            issueService.submitIssue(issue);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        } catch (IllegalStateException ex) {
            showError("Could not save your complaint. Please try again.\n"
                    + "(Check that the database is running.)");
            ex.printStackTrace();
            return;
        }

        // 5. Confirmation (submitIssue filled in the tracking code).
        JOptionPane.showMessageDialog(this,
                "Your complaint was submitted successfully!\n\n"
                        + "Tracking ID: " + issue.getTrackingCode() + "\n\n"
                        + "Please save this ID to track your complaint.",
                "Complaint Submitted", JOptionPane.INFORMATION_MESSAGE);
        clearForm();
    }

    private String validate(String category, String title,
                            String location, String description) {
        if (category == null || PLACEHOLDER.equals(category)) {
            return "Please select a category.";
        }
        if (title.isEmpty()) {
            return "Title is required.";
        }
        if (title.length() > MAX_TITLE) {
            return "Title must be at most " + MAX_TITLE + " characters.";
        }
        if (location.isEmpty()) {
            return "Location is required.";
        }
        if (location.length() > MAX_LOCATION) {
            return "Location must be at most " + MAX_LOCATION + " characters.";
        }
        if (description.isEmpty()) {
            return "Description is required.";
        }
        if (description.length() < 10) {
            return "Please describe the problem in at least 10 characters.";
        }
        if (description.length() > MAX_DESCRIPTION) {
            return "Description must be at most " + MAX_DESCRIPTION
                    + " characters.";
        }
        return null; // all good
    }

    private void clearForm() {
        categoryBox.setSelectedIndex(0);
        titleField.setText("");
        locationField.setText("");
        descriptionArea.setText("");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Cannot submit", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * TEMPORARY test launcher so you can run this screen alone.
     * Uses user id 1, so a user with id 1 must exist in app_users.
     * Real flow: login (Prasanth's module) starts the session instead.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            if (!SessionManager.isLoggedIn()) {
                SessionManager.startSession(new User(
                        1, "Test Citizen", "test@example.com",
                        null, null, "CITIZEN"));
            }
            new ComplaintSubmissionFrame().setVisible(true);
        });
    }
}
