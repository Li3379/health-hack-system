package com.hhs.security;

import com.hhs.entity.User;
import com.hhs.exception.AuthenticationCredentialsNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser != null) {
            Long userId = loginUser.getUser().getId();
            log.debug("getCurrentUserId returns: {}", userId);
            return userId;
        }
        log.warn("getCurrentUserId: loginUser is null, authentication: {}",
                SecurityContextHolder.getContext().getAuthentication());
        throw new AuthenticationCredentialsNotFoundException(
            "No authentication context found. Please login."
        );
    }

    public static User getCurrentUser() {
        LoginUser loginUser = getLoginUser();
        if (loginUser != null) {
            return loginUser.getUser();
        }
        throw new AuthenticationCredentialsNotFoundException(
            "No authentication context found. Please login."
        );
    }
}
