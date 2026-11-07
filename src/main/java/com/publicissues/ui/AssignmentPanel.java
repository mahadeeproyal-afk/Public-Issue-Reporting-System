package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;

import com.publicissues.model.Department;
import com.publicissues.model.Issue;
import com.publicissues.model.IssueStatus;
import com.publicissues.service.IssueManagementService;
import com.publicissues.service.SessionManager;

/** Administrator panel for department assignment and complaint progress. */
public final class AssignmentPanel extends JPanel {
    private final IssueManagementService service = new IssueManagementService();
    private final JComboBox<Issue> issues = new JComboBox<>();
    private final JComboBox<Department> departments = new JComboBox<>();
    private final JComboBox<IssueStatus> statuses =
            new JComboBox<>(IssueStatus.values());
    private final JTextArea remarks = new JTextArea(4, 36);

    public AssignmentPanel() {
        if (!SessionManager.isAdmin()) {
            throw new IllegalStateException(
                    "Only an authenticated administrator may open this panel.");
        }

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        issues.setRenderer(issueRenderer());

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        fields.add(new JLabel("Complaint:"));
        fields.add(issues);
        fields.add(new JLabel("Department:"));
        fields.add(departments);
        fields.add(new JLabel("New status:"));
        fields.add(statuses);
        add(fields, BorderLayout.NORTH);

        JPanel remarkPanel = new JPanel(new BorderLayout(6, 6));
        remarkPanel.add(new JLabel("Required remark:"), BorderLayout.NORTH);
        remarkPanel.add(new JScrollPane(remarks), BorderLayout.CENTER);
        add(remarkPanel, BorderLayout.CENTER);

        JButton reload = new JButton("Reload");
        JButton assign = new JButton("Assign department");
        JButton update = new JButton("Update status");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(reload);
        actions.add(assign);
        actions.add(update);
        add(actions, BorderLayout.SOUTH);

        reload.addActionListener(event -> loadData());
        assign.addActionListener(event -> assignSelected());
        update.addActionListener(event -> updateSelectedStatus());
        loadData();
    }

    private ListCellRenderer<? super Issue> issueRenderer() {
        return (list, issue, index, selected, focus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(
                            list, issue, index, selected, focus);
            if (issue != null) {
                label.setText("#" + issue.getIssueId() + " - "
                        + issue.getTitle() + " (" + issue.getStatus() + ")");
            }
            return label;
        };
    }

    private void loadData() {
        try {
            issues.removeAllItems();
            for (Issue issue : service.findIssuesForManagement()) {
                issues.addItem(issue);
            }
            departments.removeAllItems();
            for (Department department : service.findDepartments()) {
                departments.addItem(department);
            }
        } catch (RuntimeException exception) {
            showError("Could not load complaints or departments", exception);
        }
    }

    private void assignSelected() {
        Issue issue = (Issue) issues.getSelectedItem();
        Department department = (Department) departments.getSelectedItem();
        if (issue == null || department == null) {
            showMessage("Select a complaint and a department.");
            return;
        }
        try {
            service.assignDepartment(issue.getIssueId(),
                    department.getDepartmentId(),
                    SessionManager.getCurrentUserId(), remarks.getText());
            completeAction("Complaint assigned and history recorded.");
        } catch (RuntimeException exception) {
            showError("Assignment failed", exception);
        }
    }

    private void updateSelectedStatus() {
        Issue issue = (Issue) issues.getSelectedItem();
        IssueStatus status = (IssueStatus) statuses.getSelectedItem();
        if (issue == null || status == null) {
            showMessage("Select a complaint and a status.");
            return;
        }
        try {
            service.updateStatus(issue.getIssueId(),
                    SessionManager.getCurrentUserId(),
                    status.name(), remarks.getText());
            completeAction("Status updated and history recorded.");
        } catch (RuntimeException exception) {
            showError("Status update failed", exception);
        }
    }

    private void completeAction(String message) {
        remarks.setText("");
        showMessage(message);
        loadData();
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    private void showError(String heading, Exception exception) {
        JOptionPane.showMessageDialog(this,
                heading + ":\n" + exception.getMessage(),
                "Issue management", JOptionPane.ERROR_MESSAGE);
    }
}
