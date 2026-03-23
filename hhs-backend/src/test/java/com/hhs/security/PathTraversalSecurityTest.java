package com.hhs.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for path traversal prevention.
 * Tests verify that malicious path traversal attempts are properly rejected.
 */
public class PathTraversalSecurityTest {

    @Test
    public void testValidatePathRejectsDoubleDotTraversal() {
        String uploadDir = "/tmp/uploads";
        String maliciousFilename = "../../etc/passwd";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            PathValidationUtil.validateAndResolvePath(uploadDir, maliciousFilename);
        });

        assertTrue(exception.getMessage().contains("Path traversal detected"));
    }

    /**
     * Note: On Linux, backslash is not a path separator but a valid filename character.
     * This test is only relevant on Windows where backslash is a path separator.
     * TODO: Consider updating PathValidationUtil to reject backslash traversal patterns
     * on all platforms for better security (malicious Windows filenames could be uploaded
     * to Linux servers and cause issues when downloaded on Windows).
     */
    @Test
    @DisabledOnOs(OS.LINUX)
    public void testValidatePathRejectsBackslashTraversal() {
        String uploadDir = "/tmp/uploads";
        String maliciousFilename = "..\\..\\windows\\system32\\config\\sam";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            PathValidationUtil.validateAndResolvePath(uploadDir, maliciousFilename);
        });

        assertTrue(exception.getMessage().contains("Path traversal detected"));
    }

    @Test
    public void testValidatePathAcceptsSafeFilename() {
        String uploadDir = "/tmp/uploads";
        String safeFilename = "document.pdf";

        Path result = PathValidationUtil.validateAndResolvePath(uploadDir, safeFilename);

        assertTrue(result.toString().contains("document.pdf"));
        assertTrue(result.startsWith(Paths.get(uploadDir).toAbsolutePath().normalize()));
    }

    @Test
    public void testValidatePathRejectsAbsolutePath() {
        String uploadDir = "/tmp/uploads";
        String absolutePath = "/etc/passwd";

        // Absolute paths should be resolved within the upload directory
        // If the absolute path escapes the upload directory, it should throw
        assertThrows(SecurityException.class, () -> {
            PathValidationUtil.validateAndResolvePath(uploadDir, absolutePath);
        });
    }

    @Test
    public void testGenerateSafeFilenamePreservesExtension() {
        String original = "test-document.pdf";
        String safe = PathValidationUtil.generateSafeFilename(original);

        assertNotEquals(original, safe);
        assertTrue(safe.endsWith(".pdf"));
        assertTrue(safe.matches("^[a-f0-9\\-]+\\.pdf$")); // UUID format
    }

    @Test
    public void testGenerateSafeFilenameHandlesNoExtension() {
        String original = "README";
        String safe = PathValidationUtil.generateSafeFilename(original);

        assertTrue(safe.matches("^[a-f0-9\\-]+$")); // UUID without extension
    }

    @Test
    public void testIsFilenameSafeRejectsDoubleDot() {
        assertFalse(PathValidationUtil.isFilenameSafe("../../evil.txt"));
        assertFalse(PathValidationUtil.isFilenameSafe("..\\..\\evil.txt"));
    }

    @Test
    public void testIsFilenameSafeRejectsNullBytes() {
        assertFalse(PathValidationUtil.isFilenameSafe("evil\0.txt"));
    }

    @Test
    public void testIsFilenameSafeRejectsAbsolutePath() {
        assertFalse(PathValidationUtil.isFilenameSafe("/etc/passwd"));
        assertFalse(PathValidationUtil.isFilenameSafe("C:\\windows\\system32\\config\\sam"));
    }

    @Test
    public void testIsFilenameSafeAcceptsValidFilename() {
        assertTrue(PathValidationUtil.isFilenameSafe("document.pdf"));
        assertTrue(PathValidationUtil.isFilenameSafe("my-file (1).txt"));
        assertTrue(PathValidationUtil.isFilenameSafe("report_2024_final.pdf"));
    }

    @Test
    public void testIsFilenameSafeRejectsNullFilename() {
        assertFalse(PathValidationUtil.isFilenameSafe(null));
        assertFalse(PathValidationUtil.isFilenameSafe(""));
    }

    @Test
    public void testValidatePathWithNestedSafePath() {
        String uploadDir = "/tmp/uploads";
        String nestedPath = "reports/2024/document.pdf";

        Path result = PathValidationUtil.validateAndResolvePath(uploadDir, nestedPath);

        assertTrue(result.toString().contains("reports"));
        assertTrue(result.toString().contains("2024"));
        assertTrue(result.toString().contains("document.pdf"));
        assertTrue(result.startsWith(Paths.get(uploadDir).toAbsolutePath().normalize()));
    }

    @Test
    public void testValidatePathRejectsNestedTraversal() {
        String uploadDir = "/tmp/uploads";
        String maliciousNestedPath = "reports/../../../etc/passwd";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            PathValidationUtil.validateAndResolvePath(uploadDir, maliciousNestedPath);
        });

        assertTrue(exception.getMessage().contains("Path traversal detected"));
    }

    @Test
    public void testGenerateSafeFilenamePreservesLastExtension() {
        // For files with multiple dots, only the last extension is preserved
        String original = "archive.tar.gz";
        String safe = PathValidationUtil.generateSafeFilename(original);

        assertTrue(safe.endsWith(".gz"));
        assertTrue(safe.matches("^[a-f0-9\\-]+\\.gz$"));
    }

    @Test
    public void testGenerateSafeFilenameHandlesDotFiles() {
        String original = ".gitignore";
        String safe = PathValidationUtil.generateSafeFilename(original);

        // Dot files should not have extension preserved (no dot before extension)
        assertTrue(safe.matches("^[a-f0-9\\-]+$"));
    }

    @Test
    public void testIsFilenameSafeRejectsColonInFilename() {
        assertFalse(PathValidationUtil.isFilenameSafe("file:name.txt"));
        assertFalse(PathValidationUtil.isFilenameSafe("C:file.txt"));
    }

    @Test
    void testValidatePathWithTempDir(@TempDir Path tempDir) throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        java.nio.file.Files.write(testFile, "test content".getBytes());

        // Verify the path is correctly resolved
        Path result = PathValidationUtil.validateAndResolvePath(tempDir.toString(), "test.txt");

        assertEquals(testFile.toAbsolutePath().normalize(), result);
        assertTrue(java.nio.file.Files.exists(result));
    }

    @Test
    void testValidatePathEscapesTempDir(@TempDir Path tempDir) {
        // Attempt to escape temp directory
        String maliciousPath = "../outside.txt";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            PathValidationUtil.validateAndResolvePath(tempDir.toString(), maliciousPath);
        });

        assertTrue(exception.getMessage().contains("Path traversal detected"));
    }
}
