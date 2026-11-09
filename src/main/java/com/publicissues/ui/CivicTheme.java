package com.publicissues.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Shared visual tokens and small Swing styling helpers. */
public final class CivicTheme {
    public static final Color CANVAS = new Color(246, 248, 251);
    public static final Color SURFACE = Color.WHITE;
    public static final Color INK = new Color(28, 42, 57);
    public static final Color MUTED = new Color(101, 116, 132);
    public static final Color PRIMARY = new Color(26, 89, 111);
    public static final Color PRIMARY_HOVER = new Color(19, 72, 92);
    public static final Color ACCENT = new Color(40, 137, 118);
    public static final Color BORDER = new Color(220, 227, 234);
    public static final Color PALE_BLUE = new Color(235, 243, 247);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font SECTION = new Font("Segoe UI", Font.BOLD, 17);
    public static final Border FIELD_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 10, 8, 10));

    private CivicTheme() { }

    public static void install() {
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", BODY_BOLD);
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", INK);
        UIManager.put("TextField.font", BODY);
        UIManager.put("PasswordField.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.selectionBackground", PALE_BLUE);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TableHeader.font", BODY_BOLD);
        UIManager.put("TableHeader.background", PALE_BLUE);
        UIManager.put("TableHeader.foreground", INK);
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 17, 10, 17));
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(PRIMARY);
        button.setBackground(SURFACE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(9, 15, 9, 15)));
        return button;
    }

    public static void styleField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(INK);
        field.setBackground(SURFACE);
        field.setBorder(FIELD_BORDER);
    }

    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setFont(BODY);
        comboBox.setForeground(INK);
        comboBox.setBackground(SURFACE);
        comboBox.setBorder(BorderFactory.createLineBorder(BORDER));
        comboBox.setFocusable(true);
    }

    /** Creates a predictable scroll container for forms and long content. */
    public static JScrollPane scrollPane(java.awt.Component view) {
        JScrollPane pane = new JScrollPane(view,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.getVerticalScrollBar().setUnitIncrement(18);
        pane.getVerticalScrollBar().setBlockIncrement(120);
        pane.getHorizontalScrollBar().setUnitIncrement(18);
        pane.getHorizontalScrollBar().setBlockIncrement(120);
        pane.setWheelScrollingEnabled(true);
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        return pane;
    }

    public static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setForeground(INK);
        table.setBackground(SURFACE);
        table.setSelectionBackground(PALE_BLUE);
        table.setSelectionForeground(INK);
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(BORDER);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(BODY_BOLD);
        table.getTableHeader().setBackground(PALE_BLUE);
        table.getTableHeader().setForeground(INK);
        table.getTableHeader().setOpaque(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.setBorder(BorderFactory.createEmptyBorder());
    }
}
