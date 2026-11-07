package com.publicissues.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import com.publicissues.dao.UserDAO;
import com.publicissues.model.User;

class AuthServiceTest {

    @Test
    void registrationNormalizesEmailForcesCitizenRoleAndStoresBcryptHash() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        AuthService service = new AuthService(dao);

        User registered = service.registerCitizen(
                "  Test Citizen ", " TEST@EXAMPLE.COM ",
                "9876543210", "password123");

        assertEquals("Test Citizen", registered.getName());
        assertEquals("test@example.com", registered.getEmail());
        assertEquals("CITIZEN", registered.getRole());
        assertEquals(1, registered.getUserId());
        assertNull(registered.getPasswordHash());

        User stored = dao.users.get("test@example.com");
        assertNotNull(stored.getPasswordHash());
        assertNotEquals("password123", stored.getPasswordHash());
        assertTrue(BCrypt.checkpw("password123", stored.getPasswordHash()));
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        dao.users.put("taken@example.com", storedUser(
                "taken@example.com", "CITIZEN", "password123"));
        AuthService service = new AuthService(dao);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerCitizen("Test", "taken@example.com",
                        null, "password123"));
    }

    @Test
    void registrationRejectsShortPassword() {
        AuthService service = new AuthService(new InMemoryUserDAO());
        assertThrows(IllegalArgumentException.class,
                () -> service.registerCitizen("Test", "a@example.com",
                        null, "short"));
    }

    @Test
    void registrationRejectsPasswordLongerThanBcryptByteLimit() {
        AuthService service = new AuthService(new InMemoryUserDAO());
        String tooLong = "é".repeat(37); // 74 UTF-8 bytes
        assertEquals(74, tooLong.getBytes(StandardCharsets.UTF_8).length);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerCitizen("Test", "a@example.com",
                        null, tooLong));
    }

    @Test
    void registrationRejectsInvalidEmail() {
        AuthService service = new AuthService(new InMemoryUserDAO());
        assertThrows(IllegalArgumentException.class,
                () -> service.registerCitizen("Test", "not-an-email",
                        null, "password123"));
    }

    @Test
    void loginAcceptsCorrectPasswordAndRemovesHashFromReturnedUser() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        dao.users.put("user@example.com", storedUser(
                "user@example.com", "CITIZEN", "password123"));

        User result = new AuthService(dao).login(
                "USER@example.com", "password123");

        assertEquals("CITIZEN", result.getRole());
        assertNull(result.getPasswordHash());
    }

    @Test
    void loginRejectsWrongPassword() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        dao.users.put("user@example.com", storedUser(
                "user@example.com", "CITIZEN", "password123"));

        assertThrows(IllegalArgumentException.class,
                () -> new AuthService(dao).login(
                        "user@example.com", "wrong-password"));
    }

    @Test
    void loginRejectsMalformedStoredHashWithoutLeakingDetails() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        User user = storedUser("user@example.com", "CITIZEN", "password123");
        user.setPasswordHash("not-a-bcrypt-hash");
        dao.users.put(user.getEmail(), user);

        assertThrows(IllegalArgumentException.class,
                () -> new AuthService(dao).login(
                        "user@example.com", "password123"));
    }

    @Test
    void citizenLoginRejectsAdminAccount() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        dao.users.put("admin@example.com", storedUser(
                "admin@example.com", "ADMIN", "password123"));

        assertThrows(IllegalArgumentException.class,
                () -> new AuthService(dao).loginAsCitizen(
                        "admin@example.com", "password123"));
    }

    @Test
    void adminLoginRejectsCitizenAccount() {
        InMemoryUserDAO dao = new InMemoryUserDAO();
        dao.users.put("citizen@example.com", storedUser(
                "citizen@example.com", "CITIZEN", "password123"));

        assertThrows(IllegalArgumentException.class,
                () -> new AuthService(dao).loginAsAdmin(
                        "citizen@example.com", "password123"));
    }

    @Test
    void rejectsNullDao() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthService(null));
    }

    private User storedUser(String email, String role, String password) {
        return new User(12, "Test User", email,
                BCrypt.hashpw(password, BCrypt.gensalt()), null, role);
    }

    private static class InMemoryUserDAO extends UserDAO {
        private final Map<String, User> users = new HashMap<>();
        private int nextId = 1;

        @Override
        public User findByEmail(String email) throws SQLException {
            return users.get(email);
        }

        @Override
        public int registerUser(User user) throws SQLException {
            if (users.containsKey(user.getEmail())) {
                throw new SQLException("Duplicate email");
            }
            user.setUserId(nextId++);
            User stored = new User(user.getUserId(), user.getName(),
                    user.getEmail(), user.getPasswordHash(),
                    user.getPhone(), user.getRole());
            users.put(stored.getEmail(), stored);
            return user.getUserId();
        }
    }
}
