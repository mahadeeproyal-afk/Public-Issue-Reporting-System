package com.publicissues.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AdminDashboard {

    private static final String[] COLUMNS = {
        "Tracking ID", "Title", "Category", "Location", "Department", "Status", "Date"
    };

    // TEMPORARY sample data. Later this will come from MySQL.
    private static final String[][] SAMPLE_DATA = {
        {"CIV-1001", "Pothole near college",    "Road",     "Amaravati",   "Roads",       "SUBMITTED",    "2026-11-01"},
        {"CIV-1002", "Streetlight not working", "Electric", "Mangalagiri", "Electricity", "IN_PROGRESS",  "2026-11-01"},
        {"CIV-1003", "Garbage not collected",   "Garbage",  "Guntur",      "Sanitation",  "RESOLVED",     "2026-10-30"},
        {"CIV-1004", "Water pipe leakage",      "Water",    "Vijayawada",  "Water Works", "ASSIGNED",     "2026-10-31"},
        {"CIV-1005", "Drainage overflow",       "Drainage", "Tadepalli",   "Sanitation",  "UNDER_REVIEW", "2026-11-02"},
        {"CIV-1006", "Broken road divider",     "Road",     "Amaravati",   "Roads",       "SUBMITTED",    "2026-11-03"},
        {"CIV-1007", "Garbage near market",     "Garbage",  "Mangalagiri", "Sanitation",  "IN_PROGRESS",  "2026-11-02"},
        {"CIV-1008", "Fake complaint",          "Other",    "Guntur",      "-",           "REJECTED",     "2026-10-29"}
    };

    // TEMPORARY extra details, in the SAME ORDER as SAMPLE_DATA: {citizen name, phone, description}
    private static final String[][] DETAILS = {
        {"Ravi Kumar",   "9876500001", "Big pothole on the main road near the college gate. Bikes are slipping."},
        {"Anitha Reddy", "9876500002", "Streetlight has been off for 5 days. The street is very dark at night."},
        {"Suresh Babu",  "9876500003", "Garbage has not been collected for a week. There is a bad smell."},
        {"Lakshmi Devi", "9876500004", "Water pipe is leaking near the bus stop and wasting water."},
        {"Venkat Rao",   "9876500005", "Drain is overflowing onto the road after rain."},
        {"Mahesh Naidu", "9876500006", "Road divider is broken and dangerous for vehicles."},
        {"Padma Priya",  "9876500007", "Garbage is piling up near the vegetable market."},
        {"Unknown User", "9876500008", "Test complaint with no real issue."}
    };

    private static final String ALL = "All";

    // Fields so that the search, filter and details code can use them
    private static DefaultTableModel model;
    private static JTable table;
    private static JTextField searchField;
    private static JComboBox<String> statusBox;
    private static JComboBox<String> categoryBox;
    private static JComboBox<String> departmentBox;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminDashboard::createAndShow);
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("Admin Dashboard");
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

        applyFilters();
    }

    // ---------- SIDEBAR ----------
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

    // ---------- STATISTICS CARDS ----------
    private static JPanel createStatsPanel() {
        JPanel stats = new JPanel(new GridLayout(1, 4, 15, 0));
        stats.setPreferredSize(new Dimension(0, 90));

        stats.add(createCard("Total",       SAMPLE_DATA.length,         new Color(59, 130, 246)));
        stats.add(createCard("Submitted",   countStatus("SUBMITTED"),   new Color(245, 158, 11)));
        stats.add(createCard("In Progress", countStatus("IN_PROGRESS"), new Color(139, 92, 246)));
        stats.add(createCard("Resolved",    countStatus("RESOLVED"),    new Color(34, 197, 94)));
        return stats;
    }

    private static JPanel createCard(String label, int number, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(color);
        card.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel numberLabel = new JLabel(String.valueOf(number));
        numberLabel.setForeground(Color.WHITE);
        numberLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));

        JLabel textLabel = new JLabel(label);
        textLabel.setForeground(Color.WHITE);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        card.add(numberLabel, BorderLayout.CENTER);
        card.add(textLabel, BorderLayout.SOUTH);
        return card;
    }

    private static int countStatus(String status) {
        int count = 0;
        for (String[] row : SAMPLE_DATA) {
            if (row[5].equals(status)) {
                count++;
            }
        }
        return count;
    }

    // ---------- SEARCH + FILTER BAR ----------
    private static JPanel createFilterPanel() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        bar.add(new JLabel("Search:"));
        searchField = new JTextField(18);
        searchField.setToolTipText("Tracking ID, title or location");
        bar.add(searchField);

        bar.add(new JLabel("Status:"));
        statusBox = new JComboBox<>(new String[] {
            ALL, "SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "REJECTED"
        });
        bar.add(statusBox);

        bar.add(new JLabel("Category:"));
        categoryBox = new JComboBox<>(buildOptions(2));
        bar.add(categoryBox);

        bar.add(new JLabel("Department:"));
        departmentBox = new JComboBox<>(buildOptions(4));
        bar.add(departmentBox);

        JButton resetButton = new JButton("Reset");
        bar.add(resetButton);

        JButton detailsButton = new JButton("View Details");
        bar.add(detailsButton);

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

        detailsButton.addActionListener(e -> openSelectedComplaint());

        return bar;
    }

    private static String[] buildOptions(int column) {
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add(ALL);
        for (String[] row : SAMPLE_DATA) {
            if (!options.contains(row[column])) {
                options.add(row[column]);
            }
        }
        return options.toArray(new String[0]);
    }

    private static void applyFilters() {
        String text = searchField.getText().trim().toLowerCase();
        String status = (String) statusBox.getSelectedItem();
        String category = (String) categoryBox.getSelectedItem();
        String department = (String) departmentBox.getSelectedItem();

        model.setRowCount(0);

        for (String[] row : SAMPLE_DATA) {
            boolean matchesSearch =
                text.isEmpty()
                || row[0].toLowerCase().contains(text)
                || row[1].toLowerCase().contains(text)
                || row[3].toLowerCase().contains(text);

            boolean matchesStatus     = status.equals(ALL)     || row[5].equals(status);
            boolean matchesCategory   = category.equals(ALL)   || row[2].equals(category);
            boolean matchesDepartment = department.equals(ALL) || row[4].equals(department);

            if (matchesSearch && matchesStatus && matchesCategory && matchesDepartment) {
                model.addRow(row);
            }
        }
    }

    // ---------- TABLE ----------
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

        // Double-click a row to open its details
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

    // ---------- COMPLAINT DETAILS ----------
    private static void openSelectedComplaint() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(table, "Please select a complaint first.");
            return;
        }
        String trackingId = (String) model.getValueAt(selectedRow, 0);
        showDetailsDialog(trackingId);
    }

    private static void showDetailsDialog(String trackingId) {
        // Find the complaint in the sample data using its tracking ID
        int index = -1;
        for (int i = 0; i < SAMPLE_DATA.length; i++) {
            if (SAMPLE_DATA[i][0].equals(trackingId)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }
        String[] row = SAMPLE_DATA[index];
        String[] extra = DETAILS[index];

        JDialog dialog = new JDialog(
            (Frame) SwingUtilities.getWindowAncestor(table), "Complaint Details", true);
        dialog.setLayout(new BorderLayout(10, 10));

        // Top part: label + value pairs
        JPanel info = new JPanel(new GridLayout(0, 2, 10, 8));
        info.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        addDetailRow(info, "Tracking ID", row[0]);
        addDetailRow(info, "Title",       row[1]);
        addDetailRow(info, "Category",    row[2]);
        addDetailRow(info, "Location",    row[3]);
        addDetailRow(info, "Department",  row[4]);
        addDetailRow(info, "Status",      row[5]);
        addDetailRow(info, "Submitted On", row[6]);
        addDetailRow(info, "Citizen",     extra[0]);
        addDetailRow(info, "Phone",       extra[1]);
        dialog.add(info, BorderLayout.NORTH);

        // Middle part: description
        JTextArea description = new JTextArea(extra[2]);
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

        // Bottom part: close button
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setSize(480, 520);
        dialog.setLocationRelativeTo(table);
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