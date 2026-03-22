package com.hhs.integration;

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
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    private static final boolean DOCKER_AVAILABLE = checkDockerAvailable();

    // MySQL container for integration tests
    static final MySQLContainer<?> MYSQL_CONTAINER;

    // Redis container for integration tests (using GenericContainer)
    @SuppressWarnings("rawtypes")
    static final GenericContainer REDIS_CONTAINER;

    static {
        if (DOCKER_AVAILABLE) {
            log.info("Docker environment detected - starting Testcontainers");
            MYSQL_CONTAINER = new MySQLContainer<>(
                DockerImageName.parse("mysql:8.0")
            )
                .withDatabaseName("hhs_test")
                .withUsername("test")
                .withPassword("test")
                .withExposedPorts(3306);

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
     */
    private static boolean checkDockerAvailable() {
        try {
            // Try to detect Docker by checking for docker command or environment variables
            String dockerHost = System.getenv("DOCKER_HOST");
            if (dockerHost != null && !dockerHost.isEmpty()) {
                return true;
            }

            // On Windows, check if Docker Desktop is running
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Check for common Docker Desktop indicators
                String dockerPath = System.getenv("PATH");
                if (dockerPath != null && dockerPath.toLowerCase().contains("docker")) {
                    // Try to execute docker command
                    try {
                        ProcessBuilder pb = new ProcessBuilder("docker", "version");
                        Process process = pb.start();
                        boolean success = process.waitFor() == 0;
                        if (success) {
                            log.info("Docker Desktop detected on Windows");
                        }
                        return success;
                    } catch (Exception e) {
                        log.debug("Could not execute docker command: {}", e.getMessage());
                        return false;
                    }
                }
            }

            // On Linux/Mac, check for docker socket
            if (new java.io.File("/var/run/docker.sock").exists()) {
                return true;
            }

            return false;
        } catch (Exception e) {
            log.debug("Error checking Docker availability: {}", e.getMessage());
            return false;
        }
    }
}
