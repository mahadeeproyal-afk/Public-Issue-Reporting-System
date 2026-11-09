package com.publicissues;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    private static final String TEST_DATABASE = "civic_issue_test_db";
    private static final String TEST_DATABASE_URL =
            "jdbc:mysql://localhost:3306/" + TEST_DATABASE;

    private static Properties loadConfig() throws SQLException {

        Properties properties = new Properties();

        boolean testMode = Boolean.getBoolean("publicissues.testDatabase");
        String resourceName = testMode ? "test-db.properties" : "config.properties";

        try (InputStream input = DBConnection.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {

            if (input == null) {
                throw new SQLException(
                    resourceName + " file not found!"
                );
            }

            properties.load(input);

        } catch (IOException e) {
            throw new SQLException(
                "Could not load database configuration.", e
            );
        }

        return properties;
    }

    public static Connection getConnection() throws SQLException {

        Properties properties = loadConfig();

        String url = properties.getProperty("db.url");
        boolean testMode = Boolean.getBoolean("publicissues.testDatabase");

        if (testMode) {
            if (!Boolean.parseBoolean(properties.getProperty("db.testMode"))
                    || !TEST_DATABASE_URL.equals(url)) {
                throw new SQLException(
                        "Test mode requires the isolated test database URL.");
            }

            String username = System.getenv("CIVIC_ISSUE_TEST_DB_USERNAME");
            String password = System.getenv("CIVIC_ISSUE_TEST_DB_PASSWORD");
            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                throw new SQLException(
                        "Test database credentials are not configured.");
            }

            Connection connection = DriverManager.getConnection(
                    url, username, password);
            if (!TEST_DATABASE.equals(connection.getCatalog())) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    // Keep the target-validation error as the primary failure.
                }
                throw new SQLException(
                        "Refusing connection outside the isolated test database.");
            }
            return connection;
        }

        Properties productionCredentials = productionConnectionProperties(
                properties,
                System.getenv("CIVIC_ISSUE_DB_USERNAME"),
                System.getenv("CIVIC_ISSUE_DB_PASSWORD"));
        return DriverManager.getConnection(url, productionCredentials);
    }

    static Properties productionConnectionProperties(Properties configuration,
                                                      String username,
                                                      String password)
            throws SQLException {
        if (username == null || username.isBlank()) {
            throw new SQLException(
                    "Production database username is missing. Set CIVIC_ISSUE_DB_USERNAME.");
        }
        if (password == null || password.isBlank()) {
            throw new SQLException(
                    "Production database password is missing. Set CIVIC_ISSUE_DB_PASSWORD.");
        }
        if (configuration == null
                || configuration.getProperty("db.url") == null
                || configuration.getProperty("db.url").isBlank()) {
            throw new SQLException("Production database URL is not configured.");
        }

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password);
        return connectionProperties;
    }

    public static void main(String[] args) {

        try (Connection connection = getConnection()) {

            System.out.println("======================================");
            System.out.println("PUBLIC ISSUE REPORTING SYSTEM");
            System.out.println("======================================");
            System.out.println("MySQL connection successful!");
            System.out.println("Database: civic_issue_db");

        } catch (SQLException e) {

            System.out.println("Database connection failed!");
            e.printStackTrace();
        }
    }
}
