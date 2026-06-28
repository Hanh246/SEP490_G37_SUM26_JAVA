package com.sep.comiverse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    public CustomException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
