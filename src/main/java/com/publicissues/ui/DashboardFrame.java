package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.ListSelectionModel;

import com.publicissues.dao.DepartmentDAO;
import com.publicissues.model.Department;
import com.publicissues.model.Issue;
import com.publicissues.model.User;
import com.publicissues.service.IssueManagementService;
import com.publicissues.service.IssueService;
import com.publicissues.service.SessionManager;

/** Role-aware complaint dashboard. Database work is kept off the Swing EDT. */
public final class DashboardFrame extends JFrame {
    private static final String[] STATUSES = {
            "SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS",
            "RESOLVED", "REJECTED"};
    private final User user;
    private final Runnable onLogout;
    private final IssueService issueService = new IssueService();
    private final IssueManagementService managementService = new IssueManagementService();
    private final DepartmentDAO departmentDAO = new DepartmentDAO();
    private final boolean admin;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JScrollPane tableScroll;
    private final JTextField search = new JTextField(22);
    private final JLabel totalValue = new JLabel("—");
    private final JLabel pendingValue = new JLabel("—");
    private final JLabel resolvedValue = new JLabel("—");
    private final JLabel rejectedValue = new JLabel("—");
    private final TableRowSorter<DefaultTableModel> sorter;
    private final CardLayout issueCardsLayout = new CardLayout();
    private final JPanel issueCards = new JPanel(issueCardsLayout);
    private final JLabel emptyState = new JLabel("No complaints found.", JLabel.CENTER);
    private List<Issue> currentIssues = List.of();
    private boolean loggingOut;
    private boolean operationRunning;

    public DashboardFrame(User user, Runnable onLogout) {
        if (user == null || onLogout == null) throw new IllegalArgumentException("User and logout callback are required.");
        this.user = user;
        this.onLogout = onLogout;
        this.admin = "ADMIN".equals(user.getRole());
        if (!admin && !"CITIZEN".equals(user.getRole())) throw new IllegalArgumentException("Unsupported role.");
        if (!SessionManager.isLoggedIn() || SessionManager.getCurrentUserId() != user.getUserId()
                || (admin != SessionManager.isAdmin())) throw new IllegalStateException("A matching authenticated session is required.");
        setTitle("Public Issue Reporting System — " + (admin ? "Administrator" : "Citizen"));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new java.awt.Dimension(900, 600));
        setLocationRelativeTo(null);
        Object[] columns = admin
                ? new Object[]{"ID", "Tracking code", "Title", "Category", "Department", "Status", "Submitted", "Citizen"}
                : new Object[]{"ID", "Tracking code", "Title", "Category", "Department", "Status", "Submitted"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        CivicTheme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(false);
        int[] widths = admin
                ? new int[]{55, 260, 210, 125, 155, 125, 155, 180}
                : new int[]{55, 260, 210, 125, 155, 125, 155};
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
        emptyState.setFont(CivicTheme.BODY);
        emptyState.setForeground(CivicTheme.MUTED);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        table.setToolTipText("Select a complaint to see its full tracking code and details.");
        tableScroll = CivicTheme.scrollPane(table);
        tableScroll.setPreferredSize(new java.awt.Dimension(900, 120));
        tableScroll.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 380));
        sorter.addRowSorterListener(event -> updateEmptyState());
        buildUI();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { logout(); }
        });
        refresh();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(CivicTheme.CANVAS);
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(CivicTheme.CANVAS);
        JPanel titleBlock = new JPanel(new GridLayout(0, 1, 0, 3));
        titleBlock.setBackground(CivicTheme.CANVAS);
        JLabel title = new JLabel(admin ? "Issue management" : "Welcome, " + user.getName());
        title.setFont(CivicTheme.TITLE);
        JLabel subtitle = new JLabel(admin
                ? "Review reports and coordinate community response."
                : "Track the progress of the issues you have reported.");
        subtitle.setForeground(CivicTheme.MUTED);
        titleBlock.add(title);
        titleBlock.add(subtitle);
        header.add(titleBlock, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(CivicTheme.CANVAS);
        if (!admin) {
            JButton submit = CivicTheme.primaryButton("+  Submit complaint");
            submit.addActionListener(e -> submitComplaint());
            actions.add(submit);
        }
        JButton profile = CivicTheme.secondaryButton("Profile");
        profile.addActionListener(e -> showProfile());
        JButton refresh = CivicTheme.secondaryButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JButton logout = CivicTheme.secondaryButton("Log out");
        logout.addActionListener(e -> logout());
        actions.add(profile);
        actions.add(refresh);
        actions.add(logout);
        header.add(actions, BorderLayout.EAST);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setBackground(CivicTheme.SURFACE);
        JLabel listTitle = new JLabel(admin ? "All complaints" : "My complaints");
        listTitle.setFont(CivicTheme.SECTION);
        controls.add(listTitle, BorderLayout.WEST);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setBackground(CivicTheme.SURFACE);
        filters.add(new JLabel("Search"));
        filters.add(search);
        CivicTheme.styleField(search);
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{
                "All statuses", "SUBMITTED", "UNDER_REVIEW", "ASSIGNED",
                "IN_PROGRESS", "RESOLVED", "REJECTED"});
        CivicTheme.styleComboBox(statusFilter);
        filters.add(statusFilter);
        search.getDocument().addDocumentListener((SimpleDocumentListener)
                e -> applyFilter(statusFilter));
        statusFilter.addActionListener(e -> applyFilter(statusFilter));
        controls.add(filters, BorderLayout.EAST);

        JPanel issueList = new JPanel(new BorderLayout(0, 10));
        issueList.setBackground(CivicTheme.SURFACE);
        issueList.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(13, 15, 13, 15)));
        issueList.add(controls, BorderLayout.NORTH);
        issueCards.add(tableScroll, "issues");
        issueCards.add(emptyState, "empty");
        issueList.add(issueCards, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 15));
        center.setBackground(CivicTheme.CANVAS);
        center.add(buildStatistics(), BorderLayout.NORTH);
        center.add(issueList, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setBackground(CivicTheme.CANVAS);
        JButton details = CivicTheme.secondaryButton("View details and history");
        details.addActionListener(e -> showDetails());
        JButton tracking = CivicTheme.secondaryButton("Show tracking code");
        tracking.addActionListener(e -> showTrackingCode());
        bottom.add(tracking);
        bottom.add(details);
        if (admin) {
            JButton assign = CivicTheme.secondaryButton("Assign department");
            assign.addActionListener(e -> assignDepartment());
            JButton update = CivicTheme.primaryButton("Update status");
            update.addActionListener(e -> updateStatus());
            bottom.add(assign);
            bottom.add(update);
        }

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildStatistics() {
        JPanel strip = new JPanel(new GridLayout(1, 4, 12, 0));
        strip.setBackground(CivicTheme.CANVAS);
        strip.add(statisticCard("Total reports", totalValue));
        strip.add(statisticCard("Pending", pendingValue));
        strip.add(statisticCard("Resolved", resolvedValue));
        strip.add(statisticCard("Rejected", rejectedValue));
        return strip;
    }

    private JPanel statisticCard(String label, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBackground(CivicTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)));
        JLabel caption = new JLabel(label);
        caption.setForeground(CivicTheme.MUTED);
        value.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
        value.setForeground(CivicTheme.PRIMARY);
        card.add(caption, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private void refresh() {
        runAsync(() -> new DashboardData(departmentDAO.findAll(), admin
                ? issueService.getAllIssues()
                : issueService.getCitizenIssues(user.getUserId())), data -> {
            List<Department> departments = data.departments();
            List<Issue> issues = data.issues();
            departmentsCache = departments;
            currentIssues = issues;
            tableModel.setRowCount(0);
            for (Issue issue : issues) {
                Object[] row = admin
                        ? new Object[]{issue.getIssueId(), issue.getTrackingCode(),
                                issue.getTitle(), issue.getCategory(),
                                departmentName(issue.getDepartmentId()), issue.getStatus(),
                                formatDate(issue), issue.getCitizenName()}
                        : new Object[]{issue.getIssueId(), issue.getTrackingCode(),
                                issue.getTitle(), issue.getCategory(),
                                departmentName(issue.getDepartmentId()), issue.getStatus(),
                                formatDate(issue)};
                tableModel.addRow(row);
            }
            int visibleRows = Math.min(Math.max(1, issues.size()), 10);
            tableScroll.setPreferredSize(new java.awt.Dimension(900,
                    Math.max(88, 39 + visibleRows * table.getRowHeight())));
            issueCards.revalidate();
            revalidate();
            long pending = issues.stream().filter(i -> !"RESOLVED".equals(i.getStatus()) && !"REJECTED".equals(i.getStatus())).count();
            long resolved = issues.stream().filter(i -> "RESOLVED".equals(i.getStatus())).count();
            long rejected = issues.stream().filter(i -> "REJECTED".equals(i.getStatus())).count();
            totalValue.setText(Integer.toString(issues.size()));
            pendingValue.setText(Long.toString(pending));
            resolvedValue.setText(Long.toString(resolved));
            rejectedValue.setText(Long.toString(rejected));
            updateEmptyState();
        });
    }

    private String departmentName(Integer id) {
        if (id == null) return "Unassigned";
        // Department name lookup is performed from the loaded department list on demand.
        return departmentsCache.stream().filter(d -> d.getDepartmentId() == id).map(Department::getDepartmentName).findFirst().orElse("Department " + id);
    }
    private List<Department> departmentsCache = List.of();

    private String formatDate(Issue issue) {
        return issue.getCreatedAt() == null ? "—" : issue.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void applyFilter(JComboBox<String> statusFilter) {
        String text = search.getText().trim();
        RowFilter<DefaultTableModel, Integer> textFilter = text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text));
        String status = (String) statusFilter.getSelectedItem();
        RowFilter<DefaultTableModel, Integer> statusRow = status == null || "All statuses".equals(status) ? null : RowFilter.regexFilter("^" + status + "$", 5);
        if (textFilter != null && statusRow != null) sorter.setRowFilter(RowFilter.andFilter(List.of(textFilter, statusRow)));
        else sorter.setRowFilter(textFilter != null ? textFilter : statusRow);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (sorter.getViewRowCount() > 0) {
            issueCardsLayout.show(issueCards, "issues");
        } else {
            emptyState.setText(tableModel.getRowCount() == 0
                    ? (admin ? "No complaints have been submitted yet."
                            : "No complaints yet. Select Submit complaint to report an issue.")
                    : "No complaints match the current search or status filter.");
            issueCardsLayout.show(issueCards, "empty");
        }
    }

    private Issue selectedIssue() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { message("Select a complaint first.", JOptionPane.WARNING_MESSAGE); return null; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int id = (Integer) tableModel.getValueAt(modelRow, 0);
        return currentIssues.stream().filter(i -> i.getIssueId() == id).findFirst().orElse(null);
    }

    private void showTrackingCode() {
        Issue issue = selectedIssue();
        if (issue != null) {
            JTextField code = new JTextField(issue.getTrackingCode(), 32);
            code.setEditable(false);
            CivicTheme.styleField(code);
            code.selectAll();
            JOptionPane.showMessageDialog(this, code, "Complete tracking code",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showDetails() {
        Issue issue = selectedIssue(); if (issue == null) return;
        runAsync(() -> new DetailResult(issueService.getIssueDetails(issue.getIssueId()), issueService.getIssueHistory(issue.getIssueId())), result -> {
            Issue fullIssue = result.issue();
            if (fullIssue == null) { message("Complaint no longer exists.", JOptionPane.WARNING_MESSAGE); return; }
            showDetailsDialog(fullIssue, result.history());
        });
    }

    private void showDetailsDialog(Issue issue, List<String> history) {
        JDialog dialog = new JDialog(this, "Complaint details and history", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(850, screen.width - 40), Math.min(760, screen.height - 60));
        dialog.setMinimumSize(new java.awt.Dimension(500, Math.min(440, screen.height - 40)));
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(CivicTheme.CANVAS);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JLabel heading = new JLabel("Complaint overview");
        heading.setFont(CivicTheme.TITLE);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(CivicTheme.CANVAS);
        top.add(heading, BorderLayout.WEST);

        JPanel information = new JPanel(new GridLayout(0, 2, 12, 8));
        information.setBackground(CivicTheme.SURFACE);
        information.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(15, 17, 15, 17)));
        addDetail(information, "Tracking code", issue.getTrackingCode());
        addDetail(information, "Title", issue.getTitle());
        addDetail(information, "Category", issue.getCategory());
        addDetail(information, "Location", issue.getLocation());
        addDetail(information, "Current status", issue.getStatus());
        addDetail(information, "Department", departmentName(issue.getDepartmentId()));
        if (admin) {
            addDetail(information, "Reported by", issue.getCitizenName());
            addDetail(information, "Reporter email", issue.getCitizenEmail());
            addDetail(information, "Reporter phone", issue.getCitizenPhone() == null
                    ? "Not provided" : issue.getCitizenPhone());
        }

        JTextArea description = new JTextArea(issue.getDescription(), 4, 45);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(CivicTheme.BODY);
        description.setForeground(CivicTheme.INK);
        description.setBackground(CivicTheme.SURFACE);
        description.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
        description.setRows(5);
        JPanel descriptionCard = sectionCard("Description", description);

        JPanel historyEntries = new JPanel();
        historyEntries.setBackground(CivicTheme.SURFACE);
        historyEntries.setLayout(new javax.swing.BoxLayout(historyEntries, javax.swing.BoxLayout.Y_AXIS));
        if (history.isEmpty()) {
            JLabel noHistory = new JLabel("No status updates have been recorded yet.");
            noHistory.setForeground(CivicTheme.MUTED);
            historyEntries.add(noHistory);
        } else {
            for (String entry : history) {
                JTextArea item = new JTextArea(entry);
                item.setEditable(false);
                item.setLineWrap(true);
                item.setWrapStyleWord(true);
                item.setFont(CivicTheme.BODY);
                item.setForeground(CivicTheme.INK);
                item.setBackground(CivicTheme.SURFACE);
                item.setRows(Math.max(2, Math.min(6, (entry.length() / 68) + 1)));
                item.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, CivicTheme.ACCENT),
                        BorderFactory.createEmptyBorder(9, 12, 9, 8)));
                historyEntries.add(item);
                historyEntries.add(javax.swing.Box.createVerticalStrut(8));
            }
        }
        JPanel historyCard = sectionCard("Progress history", historyEntries);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.add(information);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(descriptionCard);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(historyCard);
        content.add(top, BorderLayout.NORTH);
        content.add(CivicTheme.scrollPane(body), BorderLayout.CENTER);
        JButton close = CivicTheme.secondaryButton("Close");
        close.addActionListener(event -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private void addDetail(JPanel panel, String label, String value) {
        JLabel name = new JLabel(label);
        name.setForeground(CivicTheme.MUTED);
        JLabel content = new JLabel(value == null || value.isBlank() ? "—" : value);
        content.setFont(CivicTheme.BODY_BOLD);
        panel.add(name);
        panel.add(content);
    }

    private JPanel sectionCard(String title, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBackground(CivicTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        JLabel heading = new JLabel(title);
        heading.setFont(CivicTheme.BODY_BOLD);
        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void submitComplaint() {
        runAsync(() -> departmentDAO.findAll(), departments -> {
            departmentsCache = departments;
            showSubmissionForm(departments);
        });
    }

    private void showSubmissionForm(List<Department> departments) {
        JTextField title = new JTextField(30), category = new JTextField(30), location = new JTextField(30);
        JTextArea description = new JTextArea(5, 30);
        CivicTheme.styleField(title);
        CivicTheme.styleField(category);
        CivicTheme.styleField(location);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(CivicTheme.BODY);
        JComboBox<Department> departmentBox = new JComboBox<>();
        departmentBox.addItem(null);
        departments.forEach(departmentBox::addItem);
        departmentBox.setFont(CivicTheme.BODY);
        departmentBox.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value == null ? "Choose a department (optional)" : value,
                        index, selected, focus);
            }
        });
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CivicTheme.SURFACE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        addFormField(form, c, "Title *", title);
        addFormField(form, c, "Category *", category);
        addFormField(form, c, "Location *", location);
        addFormField(form, c, "Department", departmentBox);
        addFormField(form, c, "Description *", new JScrollPane(description));
        if (JOptionPane.showConfirmDialog(this, form, "Submit a complaint", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Issue issue = new Issue(); issue.setCitizenId(user.getUserId()); issue.setTitle(title.getText().trim()); issue.setCategory(category.getText().trim()); issue.setLocation(location.getText().trim()); issue.setDescription(description.getText().trim());
        Department department = (Department) departmentBox.getSelectedItem(); if (department != null) issue.setDepartmentId(department.getDepartmentId());
        runAsync(() -> issueService.submitIssue(issue), id -> { message("Complaint submitted. Tracking code: " + issue.getTrackingCode(), JOptionPane.INFORMATION_MESSAGE); refresh(); });
    }

    private void addFormField(JPanel form, GridBagConstraints c,
                              String labelText, JComponent field) {
        c.gridy++; c.insets = new Insets(0, 0, 5, 0);
        form.add(new JLabel(labelText), c);
        c.gridy++; c.insets = new Insets(0, 0, 11, 0);
        form.add(field, c);
    }

    private void assignDepartment() {
        Issue issue = selectedIssue(); if (issue == null) return;
        runAsync(() -> departmentDAO.findAll(), departments -> {
            departmentsCache = departments;
            JComboBox<Department> choices = new JComboBox<>(departments.toArray(Department[]::new));
            if (departments.isEmpty()) { message("No departments are configured.", JOptionPane.WARNING_MESSAGE); return; }
            CivicTheme.styleComboBox(choices);
            JLabel current = new JLabel("Current assignment: " + departmentName(issue.getDepartmentId()));
            current.setForeground(CivicTheme.MUTED);
            JPanel form = actionForm("Assign complaint to a department", current,
                    new JLabel("New department"), choices);
            if (JOptionPane.showConfirmDialog(this, form, "Assign department",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            Department selected = (Department) choices.getSelectedItem();
            runAsync(() -> {
                managementService.assignDepartment(issue.getIssueId(), selected.getDepartmentId(), user.getUserId(), "Department assigned: " + selected.getDepartmentName());
                return Boolean.TRUE;
            }, ignored -> refresh());
        });
    }

    private void updateStatus() {
        Issue issue = selectedIssue(); if (issue == null) return;
        JComboBox<String> choices = new JComboBox<>(STATUSES);
        choices.setSelectedItem(issue.getStatus());
        CivicTheme.styleComboBox(choices);
        JTextArea remarksField = new JTextArea(3, 30);
        remarksField.setLineWrap(true);
        remarksField.setWrapStyleWord(true);
        remarksField.setFont(CivicTheme.BODY);
        remarksField.setBorder(CivicTheme.FIELD_BORDER);
        JPanel form = actionForm("Current status: " + issue.getStatus(),
                new JLabel("New status"), choices,
                new JLabel("Update note (optional)"), new JScrollPane(remarksField));
        if (JOptionPane.showConfirmDialog(this, form, "Update complaint status",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String next = (String) choices.getSelectedItem();
        if (next.equals(issue.getStatus())) return;
        String remarks = remarksField.getText().trim();
        runAsync(() -> { managementService.updateStatus(issue.getIssueId(), user.getUserId(), next, remarks.trim()); return Boolean.TRUE; }, ignored -> refresh());
    }

    private JPanel actionForm(String heading, JComponent... components) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CivicTheme.SURFACE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 8, 4);
        JLabel title = new JLabel(heading);
        title.setFont(CivicTheme.SECTION);
        form.add(title, c);
        c.gridy++;
        for (JComponent component : components) {
            c.insets = new Insets(4, 4, 4, 4);
            form.add(component, c);
            c.gridy++;
        }
        return form;
    }

    private void showProfile() {
        JDialog dialog = new JDialog(this, "Profile", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(470, 390);
        dialog.setMinimumSize(new java.awt.Dimension(430, 350));
        dialog.setLocationRelativeTo(this);
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(CivicTheme.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        JLabel title = new JLabel("Account profile");
        title.setFont(CivicTheme.TITLE);
        JLabel subtitle = new JLabel("Your account information");
        subtitle.setForeground(CivicTheme.MUTED);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4));
        heading.setBackground(CivicTheme.CANVAS);
        heading.add(title);
        heading.add(subtitle);
        JPanel information = new JPanel(new GridLayout(0, 1, 0, 10));
        information.setBackground(CivicTheme.SURFACE);
        information.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(17, 18, 17, 18)));
        addProfileValue(information, "Name", user.getName());
        addProfileValue(information, "Email", user.getEmail());
        addProfileValue(information, "Phone", user.getPhone() == null
                ? "Not provided" : user.getPhone());
        addProfileValue(information, "Role", admin ? "Administrator" : "Citizen");
        JButton close = CivicTheme.secondaryButton("Close");
        close.addActionListener(event -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(close);
        page.add(heading, BorderLayout.NORTH);
        page.add(information, BorderLayout.CENTER);
        page.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(page);
        dialog.setVisible(true);
    }

    private void addProfileValue(JPanel panel, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(0, 3));
        row.setBackground(CivicTheme.SURFACE);
        JLabel caption = new JLabel(label);
        caption.setForeground(CivicTheme.MUTED);
        JLabel content = new JLabel(value);
        content.setFont(CivicTheme.BODY_BOLD);
        row.add(caption, BorderLayout.NORTH);
        row.add(content, BorderLayout.CENTER);
        panel.add(row);
    }

    private <T> void runAsync(CheckedSupplier<T> work, java.util.function.Consumer<T> success) {
        if (operationRunning || loggingOut) return;
        int expectedUserId = user.getUserId();
        operationRunning = true;
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception {
                requireCurrentDashboardSession(expectedUserId);
                return work.get();
            }
            @Override protected void done() {
                operationRunning = false;
                if (!isDisplayable() || loggingOut || !isSameSession(expectedUserId)) return;
                try { success.accept(get()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); message("Operation interrupted.", JOptionPane.ERROR_MESSAGE); }
                catch (ExecutionException e) { Throwable cause = e.getCause(); message(cause instanceof IllegalArgumentException ? cause.getMessage() : "The operation failed. Check the database connection and try again.", JOptionPane.ERROR_MESSAGE); }
            }
        };
        worker.execute();
    }
    private void requireCurrentDashboardSession(int expectedUserId) {
        if (!isSameSession(expectedUserId)) throw new IllegalStateException("Session expired. Please sign in again.");
    }
    private boolean isSameSession(int expectedUserId) {
        return SessionManager.isLoggedIn() && SessionManager.getCurrentUserId() == expectedUserId
                && SessionManager.isAdmin() == admin;
    }
    private void message(String text, int type) { JOptionPane.showMessageDialog(this, text, "Public Issue Reporting System", type); }
    private void logout() { if (loggingOut) return; loggingOut = true; dispose(); onLogout.run(); }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
    private record DetailResult(Issue issue, List<String> history) { }
    private record DashboardData(List<Department> departments, List<Issue> issues) { }
    @FunctionalInterface private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void changed(javax.swing.event.DocumentEvent event);
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { changed(e); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { changed(e); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { changed(e); }
    }
}
