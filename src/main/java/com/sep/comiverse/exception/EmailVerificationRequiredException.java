package com.sep.comiverse.exception;

import org.springframework.http.HttpStatus;

public class EmailVerificationRequiredException extends CustomException {
    public static final String ERROR_CODE = "EMAIL_VERIFICATION_REQUIRED";

    public EmailVerificationRequiredException() {
        super(403, "Please verify your email before signing in.", HttpStatus.FORBIDDEN);
    }
}
