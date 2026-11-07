package com.publicissues.ui;

import com.publicissues.dao.DashboardDAO;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminDashboard {

    private static final String[] COLUMNS = {
        "Tracking ID", "Title", "Category", "Location", "Department", "Status", "Date"
    };
    private static final String ALL = "All";

    private static final DashboardDAO dao = new DashboardDAO();
    private static List<String[]> data = new ArrayList<>();

    private static JFrame frame;
    private static DefaultTableModel model;
    private static JTable table;
    private static JTextField searchField;
    private static JComboBox<String> statusBox;
    private static JComboBox<String> categoryBox;
    private static JComboBox<String> departmentBox;
    private static JLabel totalLabel, submittedLabel, inProgressLabel, resolvedLabel;
    private static boolean loading = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminDashboard::createAndShow);
    }

    private static void createAndShow() {
        frame = new JFrame("Admin Dashboard");
        frame.setSize(1100, 650);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        frame.add(createSidebar(), BorderLayout.WEST);

        JPanel mainArea = new JPanel(new BorderLayout(10, 10));
        mainArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainArea.add(createStatsPanel(), BorderLayout.NORTH);

        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.add(createFilterPanel(), BorderLayout.NORTH);
        centre.add(createTablePanel(), BorderLayout.CENTER);
        mainArea.add(centre, BorderLayout.CENTER);

        frame.add(mainArea, BorderLayout.CENTER);
        frame.setVisible(true);

        refreshData();
    }

    private static void refreshData() {
        try {
            data = dao.getAllIssueRows();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame,
                "Could not load complaints from the database:\n" + ex.getMessage(),
                "Database error", JOptionPane.ERROR_MESSAGE);
            data = new ArrayList<>();
        }
        loading = true;
        fillBox(categoryBox, 2);
        fillBox(departmentBox, 4);
        loading = false;
        updateStats();
        applyFilters();
    }

    private static void fillBox(JComboBox<String> box, int column) {
        Object previous = box.getSelectedItem();
        box.removeAllItems();
        box.addItem(ALL);
        for (String[] row : data) {
            if (!row[column].isEmpty() && !contains(box, row[column])) {
                box.addItem(row[column]);
            }
        }
        if (previous != null && contains(box, previous.toString())) {
            box.setSelectedItem(previous);
        } else {
            box.setSelectedIndex(0);
        }
    }

    private static boolean contains(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(30, 41, 59));
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JLabel title = new JLabel("Admin Panel");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(25));

        String[] menu = {"Dashboard", "Complaints", "Logout"};
        for (String name : menu) {
            JButton button = new JButton(name);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setFocusPainted(false);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(8));
        }
        return sidebar;
    }

    private static JPanel createStatsPanel() {
        JPanel stats = new JPanel(new GridLayout(1, 4, 15, 0));
        stats.setPreferredSize(new Dimension(0, 90));

        totalLabel      = new JLabel("0");
        submittedLabel  = new JLabel("0");
        inProgressLabel = new JLabel("0");
        resolvedLabel   = new JLabel("0");

        stats.add(createCard("Total",       totalLabel,      new Color(59, 130, 246)));
        stats.add(createCard("Submitted",   submittedLabel,  new Color(245, 158, 11)));
        stats.add(createCard("In Progress", inProgressLabel, new Color(139, 92, 246)));
        stats.add(createCard("Resolved",    resolvedLabel,   new Color(34, 197, 94)));
        return stats;
    }

    private static JPanel createCard(String label, JLabel numberLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(color);
        card.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        numberLabel.setForeground(Color.WHITE);
        numberLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));

        JLabel textLabel = new JLabel(label);
        textLabel.setForeground(Color.WHITE);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        card.add(numberLabel, BorderLayout.CENTER);
        card.add(textLabel, BorderLayout.SOUTH);
        return card;
    }

    private static void updateStats() {
        totalLabel.setText(String.valueOf(data.size()));
        submittedLabel.setText(String.valueOf(countStatus("SUBMITTED")));
        inProgressLabel.setText(String.valueOf(countStatus("IN_PROGRESS")));
        resolvedLabel.setText(String.valueOf(countStatus("RESOLVED")));
    }

    private static int countStatus(String status) {
        int count = 0;
        for (String[] row : data) {
            if (row[5].equals(status)) {
                count++;
            }
        }
        return count;
    }

    // Two rows: filters on top, buttons below
    private static JPanel createFilterPanel() {
        JPanel bar = new JPanel(new GridLayout(2, 1, 0, 6));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1.add(new JLabel("Search:"));
        searchField = new JTextField(16);
        searchField.setToolTipText("Tracking ID, title or location");
        row1.add(searchField);

        row1.add(new JLabel("Status:"));
        statusBox = new JComboBox<>(new String[] {
            ALL, "SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "REJECTED"
        });
        row1.add(statusBox);

        row1.add(new JLabel("Category:"));
        categoryBox = new JComboBox<>(new String[] {ALL});
        row1.add(categoryBox);

        row1.add(new JLabel("Department:"));
        departmentBox = new JComboBox<>(new String[] {ALL});
        row1.add(departmentBox);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton resetButton = new JButton("Reset");
        JButton refreshButton = new JButton("Refresh");
        JButton detailsButton = new JButton("View Details");
        row2.add(resetButton);
        row2.add(refreshButton);
        row2.add(detailsButton);

        bar.add(row1);
        bar.add(row2);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilters(); }
            public void removeUpdate(DocumentEvent e)  { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        statusBox.addActionListener(e -> applyFilters());
        categoryBox.addActionListener(e -> applyFilters());
        departmentBox.addActionListener(e -> applyFilters());

        resetButton.addActionListener(e -> {
            searchField.setText("");
            statusBox.setSelectedIndex(0);
            categoryBox.setSelectedIndex(0);
            departmentBox.setSelectedIndex(0);
            applyFilters();
        });
        refreshButton.addActionListener(e -> refreshData());
        detailsButton.addActionListener(e -> openSelectedComplaint());

        return bar;
    }

    private static void applyFilters() {
        if (loading || model == null) {
            return;
        }
        String text = searchField.getText().trim().toLowerCase();
        String status = (String) statusBox.getSelectedItem();
        String category = (String) categoryBox.getSelectedItem();
        String department = (String) departmentBox.getSelectedItem();

        model.setRowCount(0);

        for (String[] row : data) {
            boolean matchesSearch =
                text.isEmpty()
                || row[0].toLowerCase().contains(text)
                || row[1].toLowerCase().contains(text)
                || row[3].toLowerCase().contains(text);

            boolean matchesStatus     = ALL.equals(status)     || row[5].equals(status);
            boolean matchesCategory   = ALL.equals(category)   || row[2].equals(category);
            boolean matchesDepartment = ALL.equals(department) || row[4].equals(department);

            if (matchesSearch && matchesStatus && matchesCategory && matchesDepartment) {
                model.addRow(Arrays.copyOf(row, COLUMNS.length));
            }
        }
    }

    private static JScrollPane createTablePanel() {
        model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedComplaint();
                }
            }
        });

        return new JScrollPane(table);
    }

    private static void openSelectedComplaint() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a complaint first.");
            return;
        }
        String trackingId = (String) model.getValueAt(selectedRow, 0);
        for (String[] row : data) {
            if (row[0].equals(trackingId)) {
                showDetailsDialog(row);
                return;
            }
        }
    }

    private static void showDetailsDialog(String[] row) {
        JDialog dialog = new JDialog(frame, "Complaint Details", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel info = new JPanel(new GridLayout(0, 2, 10, 8));
        info.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        addDetailRow(info, "Tracking ID",  row[0]);
        addDetailRow(info, "Title",        row[1]);
        addDetailRow(info, "Category",     row[2]);
        addDetailRow(info, "Location",     row[3]);
        addDetailRow(info, "Department",   row[4]);
        addDetailRow(info, "Status",       row[5]);
        addDetailRow(info, "Submitted On", row[6]);
        addDetailRow(info, "Citizen",      row[7]);
        addDetailRow(info, "Phone",        row[8]);
        dialog.add(info, BorderLayout.NORTH);

        JTextArea description = new JTextArea(row[9]);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setEditable(false);
        description.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane descScroll = new JScrollPane(description);
        descScroll.setBorder(BorderFactory.createTitledBorder("Description"));
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        descPanel.add(descScroll, BorderLayout.CENTER);
        dialog.add(descPanel, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setSize(480, 520);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void addDetailRow(JPanel panel, String label, String value) {
        JLabel name = new JLabel(label + ":");
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(name);
        panel.add(val);
    }
}
