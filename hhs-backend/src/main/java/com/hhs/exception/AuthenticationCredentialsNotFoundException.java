package com.hhs.exception;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationCredentialsNotFoundException extends AuthenticationException {
    public AuthenticationCredentialsNotFoundException(String message) {
        super(message);
    }
}
