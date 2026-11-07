package com.publicissues.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.publicissues.model.User;

class SessionManagerTest {

    @AfterEach
    void clearSession() {
        SessionManager.logout();
    }

    @Test
    void startsCitizenSessionWithoutExposingPasswordHash() {
        User input = user(7, "CITIZEN");
        input.setPasswordHash("secret-hash");

        SessionManager.startSession(input);

        assertTrue(SessionManager.isLoggedIn());
        assertTrue(SessionManager.isCitizen());
        assertFalse(SessionManager.isAdmin());
        assertEquals(7, SessionManager.getCurrentUserId());
        assertNull(SessionManager.getCurrentUser().getPasswordHash());
    }

    @Test
    void startsAdminSession() {
        SessionManager.startSession(user(9, "ADMIN"));
        assertTrue(SessionManager.isAdmin());
        assertFalse(SessionManager.isCitizen());
    }

    @Test
    void storesAndReturnsDefensiveCopies() {
        User input = user(7, "CITIZEN");
        SessionManager.startSession(input);

        input.setName("Changed outside");
        User returned = SessionManager.getCurrentUser();
        returned.setName("Changed by caller");

        assertEquals("Test User", SessionManager.getCurrentUser().getName());
    }

    @Test
    void rejectsNullUser() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionManager.startSession(null));
    }

    @Test
    void rejectsNonPositiveUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionManager.startSession(user(0, "CITIZEN")));
    }

    @Test
    void rejectsUnsupportedRole() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionManager.startSession(user(1, "SUPERUSER")));
    }

    @Test
    void logoutClearsSession() {
        SessionManager.startSession(user(7, "CITIZEN"));
        SessionManager.logout();

        assertFalse(SessionManager.isLoggedIn());
        assertFalse(SessionManager.isCitizen());
        assertFalse(SessionManager.isAdmin());
        assertNull(SessionManager.getCurrentUser());
        assertThrows(IllegalStateException.class,
                SessionManager::getCurrentUserId);
    }

    private User user(int id, String role) {
        return new User(id, "Test User", "test@example.invalid",
                null, null, role);
    }
}
