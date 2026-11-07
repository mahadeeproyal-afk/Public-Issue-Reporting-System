package com.publicissues.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.publicissues.DBConnection;
import com.publicissues.model.User;

public class UserDAO {

    // Register a new user in the database.
    // passwordHash must already be BCrypt-hashed by the service layer.
    public int registerUser(User user) throws SQLException {

        String sql = """
                INSERT INTO app_users
                (name, email, password_hash, phone, role)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getRole());

            int rowsInserted = statement.executeUpdate();

            if (rowsInserted == 0) {
                throw new SQLException("User registration failed.");
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int userId = keys.getInt(1);
                    user.setUserId(userId);
                    return userId;
                }
            }
        }

        throw new SQLException("User ID could not be generated.");
    }

    // Find a user using their email address.
    public User findByEmail(String email) throws SQLException {

        String sql = """
                SELECT user_id, name, email, password_hash, phone, role
                FROM app_users
                WHERE email = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return mapUser(result);
                }
            }
        }

        return null;
    }

    // Find a user using their database ID.
    public User findById(int userId) throws SQLException {

        String sql = """
                SELECT user_id, name, email, password_hash, phone, role
                FROM app_users
                WHERE user_id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return mapUser(result);
                }
            }
        }

        return null;
    }

    // Find a user using the caller's transaction connection.
    public User findById(Connection connection, int userId)
            throws SQLException {

        String sql = """
                SELECT user_id, name, email, password_hash, phone, role
                FROM app_users
                WHERE user_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapUser(result);
                }
            }
        }

        return null;
    }

    // Convert a database row into a Java User object.
    private User mapUser(ResultSet result) throws SQLException {

        User user = new User();

        user.setUserId(result.getInt("user_id"));
        user.setName(result.getString("name"));
        user.setEmail(result.getString("email"));
        user.setPasswordHash(result.getString("password_hash"));
        user.setPhone(result.getString("phone"));
        user.setRole(result.getString("role"));

        return user;
    }
}
