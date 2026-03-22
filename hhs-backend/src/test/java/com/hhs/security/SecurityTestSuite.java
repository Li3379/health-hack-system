package com.hhs.security;

import com.hhs.exception.AuthenticationCredentialsNotFoundException;
import com.hhs.util.SecurityTestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityTestSuite {

    @Test
    public void testGetCurrentUserIdThrowsWhenNoAuthContext() {
        // Clear any existing authentication
        SecurityContextHolder.clearContext();

        // Should throw exception, not return hardcoded user ID
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> {
            SecurityUtils.getCurrentUserId();
        });
    }

    @Test
    public void testGetCurrentUserThrowsWhenNoAuthContext() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> {
            SecurityUtils.getCurrentUser();
        });
    }

    @Test
    public void testGetCurrentUserIdReturnsUserIdWhenAuthenticated() {
        // Set up authentication
        SecurityTestUtil.setAuthentication(123L, "testuser");

        // Should return the user ID
        Long userId = SecurityUtils.getCurrentUserId();
        assertEquals(123L, userId);

        // Clean up
        SecurityTestUtil.clearAuthentication();
    }

    @Test
    public void testGetCurrentUserReturnsUserWhenAuthenticated() {
        // Set up authentication
        SecurityTestUtil.setAuthentication(456L, "testuser2");

        // Should return the user
        var user = SecurityUtils.getCurrentUser();
        assertNotNull(user);
        assertEquals(456L, user.getId());
        assertEquals("testuser2", user.getUsername());

        // Clean up
        SecurityTestUtil.clearAuthentication();
    }

    @Test
    public void testGetLoginUserReturnsNullWhenNoAuthContext() {
        SecurityContextHolder.clearContext();

        // getLoginUser should return null (for internal use)
        assertNull(SecurityUtils.getLoginUser());
    }

    @Test
    public void testSecurityTestUtilSetsAuthenticationCorrectly() {
        SecurityContextHolder.clearContext();

        // Set authentication using test util
        SecurityTestUtil.setAuthentication(789L, "utiltest");

        // Verify it was set
        var loginUser = SecurityUtils.getLoginUser();
        assertNotNull(loginUser);
        assertEquals(789L, loginUser.getUser().getId());
        assertEquals("utiltest", loginUser.getUser().getUsername());

        // Clean up
        SecurityTestUtil.clearAuthentication();
    }
}
