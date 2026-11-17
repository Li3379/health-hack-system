package com.hhs.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code src/test/resources/sql/schema-test.sql} in lockstep with the production
 * schema.
 *
 * <p>Integration tests cannot execute the production file directly — Testcontainers'
 * {@code ScriptUtils} rejects the mysql client's {@code DELIMITER} directive, which the
 * two maintenance stored procedures require. Rather than let the test copy silently
 * drift, this test asserts the only permitted difference is the removal of that one
 * DELIMITER-delimited block.</p>
 *
 * <p>Tagged {@code unit} rather than {@code integration}: it touches only the classpath
 * and must run in the fast suite so drift is caught long before anyone starts Docker.</p>
 */
@Tag("unit")
class SchemaTestScriptParityTest {

    private static final String PROD_SCHEMA = "sql/schema.sql";
    private static final String TEST_SCHEMA = "sql/schema-test.sql";

    @Test
    @DisplayName("schema-test.sql equals schema.sql minus exactly the stored-procedure block")
    void testSchemaMatchesProductionExceptStoredProcedures() throws IOException {
        String prod = read(PROD_SCHEMA);
        String test = read(TEST_SCHEMA);

        int delimiterStart = prod.indexOf("DELIMITER //");
        int delimiterEnd = prod.indexOf("DELIMITER ;");
        assertThat(delimiterStart)
            .as("production schema must contain a DELIMITER // block to strip")
            .isGreaterThanOrEqualTo(0);
        assertThat(delimiterEnd)
            .as("production schema must close its DELIMITER block")
            .isGreaterThan(delimiterStart);

        int blockEnd = prod.indexOf('\n', delimiterEnd) + 1;
        String expectedBase = prod.substring(0, delimiterStart) + prod.substring(blockEnd);

        // The test schema appends one documented test-only seed block. Everything up
        // to that marker must be byte-identical to production-minus-procedures.
        int seedMarker = test.indexOf("-- TEST-ONLY SEED");
        assertThat(seedMarker)
            .as("schema-test.sql must keep its '-- TEST-ONLY SEED' marker so this test can find the boundary")
            .isGreaterThanOrEqualTo(0);

        assertThat(test.substring(0, seedMarker))
            .as(
                "schema-test.sql has drifted from schema.sql. Regenerate it: take "
                    + "src/main/resources/sql/schema.sql and delete the single block from "
                    + "'DELIMITER //' through the line after 'DELIMITER ;'. Do not hand-edit "
                    + "individual statements.")
            .isEqualTo(expectedBase);
    }

    @Test
    @DisplayName("the test schema still creates every table and no stored procedures")
    void testSchemaKeepsAllTablesAndDropsProcedures() throws IOException {
        String prod = read(PROD_SCHEMA);
        String test = read(TEST_SCHEMA);

        assertThat(countOccurrences(test, "CREATE TABLE"))
            .as("every production table must survive into the test schema")
            .isEqualTo(countOccurrences(prod, "CREATE TABLE"));

        assertThat(countOccurrences(test, "CREATE PROCEDURE"))
            .as("stored procedures must be absent — ScriptUtils cannot parse DELIMITER")
            .isZero();
        assertThat(countOccurrences(test, "DELIMITER"))
            .as("no DELIMITER directive may remain")
            .isZero();
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = SchemaTestScriptParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("classpath resource %s must exist", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
