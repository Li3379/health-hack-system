package com.hhs.security;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Utility class for secure file path handling.
 * Prevents path traversal attacks by validating and sanitizing file paths.
 */
public class PathValidationUtil {

    /**
     * Validates and resolves a file path within the upload directory.
     * Prevents path traversal attacks by checking if the resolved path
     * stays within the intended upload directory.
     *
     * @param uploadDir The base upload directory (absolute path)
     * @param filename The user-provided filename (may contain malicious sequences)
     * @return A safe, resolved Path within the upload directory
     * @throws SecurityException if path traversal attempt detected
     */
    public static Path validateAndResolvePath(String uploadDir, String filename) {
        // Get absolute, normalized upload directory path
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // Normalize the input filename to resolve any ../ sequences
        Path inputPath = Paths.get(filename).normalize();

        // Resolve the input path against upload directory
        Path resolvedPath = uploadPath.resolve(inputPath).normalize();

        // Verify the resolved path is still within upload directory
        if (!resolvedPath.startsWith(uploadPath)) {
            throw new SecurityException(
                "Path traversal detected in filename: " + filename + ". " +
                "Resolved path: " + resolvedPath + " escapes upload directory: " + uploadPath
            );
        }

        return resolvedPath;
    }

    /**
     * Generates a safe UUID-based filename while preserving the original extension.
     *
     * @param originalFilename The user-provided filename
     * @return A UUID-based filename with original extension (e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf")
     */
    public static String generateSafeFilename(String originalFilename) {
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFilename.substring(lastDotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Validates if a filename contains suspicious patterns that may indicate attack attempts.
     *
     * @param filename The filename to validate
     * @return true if the filename appears safe, false otherwise
     */
    public static boolean isFilenameSafe(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // Check for obvious path traversal patterns
        if (filename.contains("../") || filename.contains("..\\")) {
            return false;
        }

        // Check for absolute paths
        if (filename.startsWith("/") || filename.contains(":")) {
            return false;
        }

        // Check for null bytes (attempt to bypass string validation)
        if (filename.contains("\0")) {
            return false;
        }

        return true;
    }

    private PathValidationUtil() {} // Prevent instantiation
}
