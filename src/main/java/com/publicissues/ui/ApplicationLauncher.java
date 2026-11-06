package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.publicissues.model.User;
import com.publicissues.service.SessionManager;

public final class ApplicationLauncher {

    private ApplicationLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ApplicationLauncher::openLogin);
    }

    private static void openLogin() {
        new LoginFrame(ApplicationLauncher::openRolePlaceholder)
                .setVisible(true);
    }

    private static void openRolePlaceholder(User user) {
        String roleLabel = SessionManager.isAdmin()
                ? "Administrator"
                : "Citizen";

        JFrame placeholder = new JFrame(
                "Public Issue Reporting System - " + roleLabel);
        placeholder.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        placeholder.setSize(520, 260);
        placeholder.setLocationRelativeTo(null);
        placeholder.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(
                30, 30, 30, 30));

        JLabel message = new JLabel(
                roleLabel + " dashboard placeholder",
                SwingConstants.CENTER);
        JLabel details = new JLabel(
                "The " + roleLabel.toLowerCase()
                        + " dashboard will be provided by its owning module.",
                SwingConstants.CENTER);
        JButton logoutButton = new JButton("Logout");
        boolean[] logoutStarted = {false};

        JPanel textPanel = new JPanel(new BorderLayout(8, 8));
        textPanel.add(message, BorderLayout.CENTER);
        textPanel.add(details, BorderLayout.SOUTH);

        JPanel actionPanel = new JPanel();
        actionPanel.add(logoutButton);

        content.add(textPanel, BorderLayout.CENTER);
        content.add(actionPanel, BorderLayout.SOUTH);
        placeholder.add(content);

        Runnable logout = () -> {
            if (logoutStarted[0]) {
                return;
            }
            logoutStarted[0] = true;
            SessionManager.logout();
            placeholder.dispose();
            openLogin();
        };

        logoutButton.addActionListener(event -> logout.run());
        placeholder.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                logout.run();
            }
        });
        placeholder.setVisible(true);
    }
}
