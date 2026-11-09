package com.publicissues.ui;

import javax.swing.SwingUtilities;

import com.publicissues.model.User;
import com.publicissues.service.SessionManager;

public final class ApplicationLauncher {

    private ApplicationLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CivicTheme.install();
            openLogin();
        });
    }

    private static void openLogin() {
        new LoginFrame(ApplicationLauncher::openDashboard)
                .setVisible(true);
    }

    private static void openDashboard(User user) {
        if (!SessionManager.isLoggedIn()
                || user.getUserId() != SessionManager.getCurrentUserId()) {
            SessionManager.logout();
            openLogin();
            return;
        }
        new DashboardFrame(user, () -> {
            SessionManager.logout();
            openLogin();
        }).setVisible(true);
    }
}
