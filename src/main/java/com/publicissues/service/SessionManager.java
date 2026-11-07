package com.publicissues.service;

import com.publicissues.model.User;

public final class SessionManager {

    private static final String CITIZEN_ROLE = "CITIZEN";
    private static final String ADMIN_ROLE = "ADMIN";

    private static User currentUser;

    private SessionManager() {
    }

    public static synchronized void startSession(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        if (user.getUserId() <= 0) {
            throw new IllegalArgumentException("User ID must be positive.");
        }
        if (!CITIZEN_ROLE.equals(user.getRole())
                && !ADMIN_ROLE.equals(user.getRole())) {
            throw new IllegalArgumentException("Unsupported user role.");
        }

        currentUser = copyWithoutPasswordHash(user);
    }

    public static synchronized User getCurrentUser() {
        if (currentUser == null) {
            return null;
        }
        return copyWithoutPasswordHash(currentUser);
    }

    public static synchronized int getCurrentUserId() {
        if (currentUser == null) {
            throw new IllegalStateException("No active session.");
        }
        return currentUser.getUserId();
    }

    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public static synchronized boolean isAdmin() {
        return currentUser != null
                && ADMIN_ROLE.equals(currentUser.getRole());
    }

    public static synchronized boolean isCitizen() {
        return currentUser != null
                && CITIZEN_ROLE.equals(currentUser.getRole());
    }

    public static synchronized void logout() {
        currentUser = null;
    }

    private static User copyWithoutPasswordHash(User user) {
        return new User(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                null,
                user.getPhone(),
                user.getRole());
    }
}
