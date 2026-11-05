package com.publicissues.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

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

    private static final String ALL = "All";

    // These are fields so that the search and filter code can use them
    private static DefaultTableModel model;
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

        // Centre: filter bar on top, table below
        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.add(createFilterPanel(), BorderLayout.NORTH);
        centre.add(createTablePanel(), BorderLayout.CENTER);
        mainArea.add(centre, BorderLayout.CENTER);

        frame.add(mainArea, BorderLayout.CENTER);
        frame.setVisible(true);

        applyFilters(); // show the full list at start
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
        categoryBox = new JComboBox<>(buildOptions(2)); // column 2 = Category
        bar.add(categoryBox);

        bar.add(new JLabel("Department:"));
        departmentBox = new JComboBox<>(buildOptions(4)); // column 4 = Department
        bar.add(departmentBox);

        JButton resetButton = new JButton("Reset");
        bar.add(resetButton);

        // Run the filter whenever the admin types or chooses something
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

        return bar;
    }

    // Builds a dropdown list ("All" + each different value in that column)
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

    // The main filter method: rebuilds the table using the search + filters
    private static void applyFilters() {
        String text = searchField.getText().trim().toLowerCase();
        String status = (String) statusBox.getSelectedItem();
        String category = (String) categoryBox.getSelectedItem();
        String department = (String) departmentBox.getSelectedItem();

        model.setRowCount(0); // clear the table

        for (String[] row : SAMPLE_DATA) {
            boolean matchesSearch =
                text.isEmpty()
                || row[0].toLowerCase().contains(text)   // tracking ID
                || row[1].toLowerCase().contains(text)   // title
                || row[3].toLowerCase().contains(text);  // location

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

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        return new JScrollPane(table);
    }
}