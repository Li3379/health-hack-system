package com.hhs.integration;

import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests
 * Uses Testcontainers for isolated MySQL and Redis instances when Docker is available
 * Falls back to localhost connections when Docker is not available
 * All integration tests should extend this class to inherit test configuration
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

    /**
     * Schema applied to the throwaway MySQL container before any test runs.
     *
     * <p>This is a generated twin of {@code src/main/resources/sql/schema.sql} with the
     * two maintenance stored procedures removed: Testcontainers' {@code ScriptUtils}
     * does not understand the mysql client's {@code DELIMITER} directive, and the
     * procedures are unreachable from application code. A parity test keeps the two
     * files in sync.</p>
     */
    private static final String TEST_SCHEMA_SCRIPT = "sql/schema-test.sql";

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    private static final boolean DOCKER_AVAILABLE;

    static {
        ensureDockerApiVersion();
        DOCKER_AVAILABLE = checkDockerAvailable();
    }

    // MySQL container for integration tests
    static final MySQLContainer<?> MYSQL_CONTAINER;

    // Redis container for integration tests (using GenericContainer)
    @SuppressWarnings("rawtypes")
    static final GenericContainer REDIS_CONTAINER;

    /**
     * Minimum Docker Engine API version this build must talk.
     *
     * <p>docker-java 3.3.4 (pinned by Testcontainers 1.19.3) defaults to API 1.32.
     * Docker Engine 29.x raised its minimum to 1.40 and answers any older request
     * with {@code 400 Bad Request}, which Testcontainers reports as
     * "Could not find a valid Docker environment". Setting this before the first
     * client is created fixes the negotiation without upgrading dependencies.</p>
     *
     * <p>An explicit {@code api.version} system property (or {@code DOCKER_API_VERSION})
     * always wins, so a CI image with an older Engine can still override it.</p>
     */
    private static void ensureDockerApiVersion() {
        if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
            System.setProperty("api.version", "1.44");
            log.info("Defaulted Docker API version to 1.44 (docker-java 3.3.4 defaults to 1.32, "
                + "which Docker Engine 29.x rejects with HTTP 400)");
        }
    }

    static {
        if (DOCKER_AVAILABLE) {
            log.info("Docker environment detected - starting Testcontainers");
            MYSQL_CONTAINER = new MySQLContainer<>(
                DockerImageName.parse("mysql:8.0")
            )
                .withDatabaseName("hhs_test")
                .withUsername("test")
                .withPassword("test")
                .withExposedPorts(3306)
                // Without this the container starts empty and every integration test
                // fails at Spring context load with "Table 'hhs_test.<x>' doesn't exist".
                .withInitScript(TEST_SCHEMA_SCRIPT);

            REDIS_CONTAINER = new GenericContainer(
                DockerImageName.parse("redis:7-alpine")
            )
                .withExposedPorts(6379);

            // Start containers before Spring context loads
            MYSQL_CONTAINER.start();
            REDIS_CONTAINER.start();

            // Set system properties for Spring to use Testcontainers URLs
            System.setProperty("TEST_DB_URL", MYSQL_CONTAINER.getJdbcUrl());
            System.setProperty("TEST_DB_USER", MYSQL_CONTAINER.getUsername());
            System.setProperty("TEST_DB_PASSWORD", MYSQL_CONTAINER.getPassword());
            System.setProperty("TEST_REDIS_HOST", REDIS_CONTAINER.getHost());
            System.setProperty("TEST_REDIS_PORT", String.valueOf(REDIS_CONTAINER.getFirstMappedPort()));

            log.info("Testcontainers started - MySQL: {}, Redis: {}:{}",
                MYSQL_CONTAINER.getJdbcUrl(), REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getFirstMappedPort());
        } else {
            log.info("Docker not available - using localhost connections for integration tests");
            MYSQL_CONTAINER = null;
            REDIS_CONTAINER = null;

            // Use default values from application-test.yml (localhost connections)
            System.setProperty("TEST_DB_URL", "jdbc:mysql://localhost:3306/hhs_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
            System.setProperty("TEST_DB_USER", "root");
            System.setProperty("TEST_DB_PASSWORD", "test");
            System.setProperty("TEST_REDIS_HOST", "localhost");
            System.setProperty("TEST_REDIS_PORT", "6379");
        }
    }

    /**
     * Check if Docker environment is available for Testcontainers
     * Detects Docker in various CI environments (GitHub Actions, GitLab CI, etc.)
     */
    private static boolean checkDockerAvailable() {
        try {
            // Check for CI environment indicators that have Docker available
            String ci = System.getenv("CI");
            if ("true".equals(ci)) {
                // Most CI environments with Docker set CI=true
                // GitHub Actions, GitLab CI, CircleCI, etc.
                String githubActions = System.getenv("GITHUB_ACTIONS");
                if ("true".equals(githubActions)) {
                    log.info("GitHub Actions environment detected - Docker available");
                    return true;
                }

                String gitlabCi = System.getenv("GITLAB_CI");
                if ("true".equals(gitlabCi)) {
                    log.info("GitLab CI environment detected - Docker available");
                    return true;
                }

                String circleCi = System.getenv("CIRCLECI");
                if ("true".equals(circleCi)) {
                    log.info("CircleCI environment detected - Docker available");
                    return true;
                }
            }

            // Check DOCKER_HOST environment variable (Docker Desktop, remote Docker)
            String dockerHost = System.getenv("DOCKER_HOST");
            if (dockerHost != null && !dockerHost.isEmpty()) {
                log.info("DOCKER_HOST environment variable detected: {}", dockerHost);
                return true;
            }

            // Check Testcontainers-specific environment variable
            String tcEnabled = System.getenv("TESTCONTAINERS_ENABLED");
            if ("true".equalsIgnoreCase(tcEnabled)) {
                log.info("TESTCONTAINERS_ENABLED=true - Docker explicitly enabled");
                return true;
            }

            // On Linux, check for docker socket
            if (new java.io.File("/var/run/docker.sock").exists()) {
                log.info("Docker socket found at /var/run/docker.sock");
                return true;
            }

            // On Windows, check if Docker Desktop is running
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Try to execute docker command to verify Docker is running
                try {
                    ProcessBuilder pb = new ProcessBuilder("docker", "version");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    boolean success = process.waitFor() == 0;
                    if (success) {
                        log.info("Docker Desktop detected on Windows");
                    }
                    return success;
                } catch (Exception e) {
                    log.debug("Could not execute docker command: {}", e.getMessage());
                }
            }

            // On macOS, check for Docker Desktop socket
            String userHome = System.getProperty("user.home");
            if (new java.io.File(userHome + "/.docker/run/docker.sock").exists()) {
                log.info("Docker Desktop socket found on macOS");
                return true;
            }

            log.info("Docker not detected - integration tests will use fallback configuration");
            return false;
        } catch (Exception e) {
            log.debug("Error checking Docker availability: {}", e.getMessage());
            return false;
        }
    }
}
