package com.hhs.util;

import com.hhs.entity.User;
import com.hhs.security.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityTestUtil {

    public static void setAuthentication(Long userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        LoginUser loginUser = new LoginUser(user);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private SecurityTestUtil() {} // Prevent instantiation
}
