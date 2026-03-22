package com.hhs.integration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database integration tests to verify schema and migrations
 */
public class DatabaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDatabaseConnection() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isValid(5), "Database connection should be valid");
            assertFalse(conn.isClosed(), "Connection should not be closed");
        }
    }

    @Test
    public void testTablesExist() throws Exception {
        List<String> requiredTables = Arrays.asList(
            "sys_user",
            "health_profile",
            "health_metric",
            "realtime_metric",
            "health_alert",
            "alert_rule",
            "risk_assessment",
            "examination_report",
            "lab_result",
            "user_threshold",
            "ocr_recognize_log"
        );

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String table : requiredTables) {
                ResultSet rs = stmt.executeQuery(
                    "SHOW TABLES LIKE '" + table + "'"
                );
                assertTrue(rs.next(), "Table " + table + " should exist");
                rs.close();
            }
        }
    }

    @Test
    public void testIndexesExist() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check for important indexes
            String[] expectedIndexes = {
                "sys_user",
                "realtime_metric",
                "health_metric",
                "health_alert"
            };

            for (String table : expectedIndexes) {
                ResultSet rs = stmt.executeQuery(
                    "SHOW INDEX FROM " + table
                );
                assertTrue(rs.next(), "Table " + table + " should have at least one index");
                rs.close();
            }
        }
    }

    @Test
    public void testForeignKeyConstraints() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check referential constraints
            ResultSet rs = stmt.executeQuery(
                "SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL"
            );

            int constraintCount = 0;
            while (rs.next()) {
                constraintCount++;
            }
            rs.close();

            assertTrue(constraintCount > 0, "Should have foreign key constraints defined");
        }
    }

    @Test
    public void testTableEngines() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME, ENGINE " +
                "FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_TYPE = 'BASE TABLE'"
            );

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String engine = rs.getString("ENGINE");
                // Most tables should use InnoDB for transaction support
                // Skip views and summary tables which may have null engine
                if (engine != null && !tableName.startsWith("act_") && !tableName.startsWith("v_")) {
                    assertEquals("InnoDB", engine, "Table " + tableName + " should use InnoDB engine");
                }
            }
            rs.close();
        }
    }
}
