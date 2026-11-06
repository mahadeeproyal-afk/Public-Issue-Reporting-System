package com.publicissues.service;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.mindrot.jbcrypt.BCrypt;

import com.publicissues.dao.UserDAO;
import com.publicissues.model.User;

public class AuthService {

    private static final String CITIZEN_ROLE = "CITIZEN";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9][0-9() .-]{6,14}[0-9]$");

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    /**
     * Constructor for controlled DAO substitution in unit tests and integrations.
     */
    public AuthService(UserDAO userDAO) {
        if (userDAO == null) {
            throw new IllegalArgumentException("UserDAO is required.");
        }
        this.userDAO = userDAO;
    }

    public User registerCitizen(String name, String email, String phone,
                                String password) {
        String normalizedName = validateName(name);
        String normalizedEmail = validateEmail(email);
        String normalizedPhone = validatePhone(phone);
        validateRegistrationPassword(password);

        try {
            if (userDAO.findByEmail(normalizedEmail) != null) {
                throw new IllegalArgumentException(
                        "An account with that email already exists.");
            }

            User user = new User();
            user.setName(normalizedName);
            user.setEmail(normalizedEmail);
            user.setPhone(normalizedPhone);
            user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
            user.setRole(CITIZEN_ROLE);

            userDAO.registerUser(user);
            clearPasswordHash(user);
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Unable to register the account.", e);
        }
    }

    public User login(String email, String password) {
        String normalizedEmail = validateEmail(email);
        validateLoginPassword(password);

        try {
            User user = userDAO.findByEmail(normalizedEmail);
            if (user == null || !matchesPassword(password, user)) {
                throw invalidCredentials();
            }
            clearPasswordHash(user);
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to authenticate.", e);
        }
    }

    public User loginAsCitizen(String email, String password) {
        return loginWithRequiredRole(email, password, CITIZEN_ROLE);
    }

    public User loginAsAdmin(String email, String password) {
        return loginWithRequiredRole(email, password, ADMIN_ROLE);
    }

    private User loginWithRequiredRole(String email, String password,
                                       String requiredRole) {
        User user = login(email, password);
        if (!requiredRole.equals(user.getRole())) {
            throw invalidCredentials();
        }
        return user;
    }

    private boolean matchesPassword(String password, User user) {
        String passwordHash = user.getPasswordHash();
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void clearPasswordHash(User user) {
        user.setPasswordHash(null);
    }

    private IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("Invalid email or password.");
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Name is required.");
        }
        return name.trim();
    }

    private String validateEmail(String email) {
        if (email == null || email.isBlank()
                || email.trim().length() > 150
                || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("A valid email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        if (phone.trim().length() > 15
                || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("A valid phone number is required.");
        }
        return phone.trim();
    }

    private void validateRegistrationPassword(String password) {
        if (password == null || password.isBlank()
                || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters and no more "
                            + "than 72 UTF-8 bytes.");
        }
    }

    private void validateLoginPassword(String password) {
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must not be blank or exceed 72 UTF-8 bytes.");
        }
    }
}
