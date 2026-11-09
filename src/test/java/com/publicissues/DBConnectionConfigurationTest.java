package com.publicissues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class DBConnectionConfigurationTest {

    @Test
    void missingProductionUsernameFailsClearly() {
        SQLException error = assertThrows(SQLException.class,
                () -> DBConnection.productionConnectionProperties(
                        configurationWithLegacyCredentials(), null, "synthetic-test-password"));
        assertTrue(error.getMessage().contains("CIVIC_ISSUE_DB_USERNAME"));
    }

    @Test
    void blankProductionPasswordFailsClearly() {
        SQLException error = assertThrows(SQLException.class,
                () -> DBConnection.productionConnectionProperties(
                        configurationWithLegacyCredentials(), "synthetic-test-user", "  "));
        assertTrue(error.getMessage().contains("CIVIC_ISSUE_DB_PASSWORD"));
    }

    @Test
    void missingProductionPasswordFailsClearly() {
        SQLException error = assertThrows(SQLException.class,
                () -> DBConnection.productionConnectionProperties(
                        configurationWithLegacyCredentials(), "synthetic-test-user", null));
        assertTrue(error.getMessage().contains("CIVIC_ISSUE_DB_PASSWORD"));
    }

    @Test
    void productionCredentialsComeOnlyFromEnvironmentInputs() throws SQLException {
        Properties connectionProperties = DBConnection.productionConnectionProperties(
                configurationWithLegacyCredentials(),
                "environment-test-user", "environment-test-password");

        assertEquals("environment-test-user", connectionProperties.getProperty("user"));
        assertEquals("environment-test-password", connectionProperties.getProperty("password"));
        assertFalse(connectionProperties.containsKey("db.username"));
        assertFalse(connectionProperties.containsKey("db.password"));
        assertFalse(connectionProperties.containsKey("db.url"));
    }

    @Test
    void missingProductionUrlFailsClearly() {
        SQLException error = assertThrows(SQLException.class,
                () -> DBConnection.productionConnectionProperties(
                        new Properties(), "synthetic-test-user", "synthetic-test-password"));
        assertEquals("Production database URL is not configured.", error.getMessage());
    }

    private Properties configurationWithLegacyCredentials() {
        Properties configuration = new Properties();
        configuration.setProperty("db.url", "jdbc:mysql://example.invalid/civic_issue_db");
        configuration.setProperty("db.username", "ignored-config-user");
        configuration.setProperty("db.password", "ignored-config-password");
        return configuration;
    }
}
