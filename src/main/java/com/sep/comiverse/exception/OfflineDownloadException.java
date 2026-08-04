package com.sep.comiverse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OfflineDownloadException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public OfflineDownloadException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
