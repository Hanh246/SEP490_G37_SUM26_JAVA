package com.sep.comiverse.exception;

import com.sep.comiverse.dto.response.BaseResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            AsyncRequestNotUsableException.class,
            ClientAbortException.class
    })
    public void handleClientDisconnected(Exception ex) {
        log.debug("Client disconnected before response completed: {}", ex.getMessage());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Object>> handleCustomException(CustomException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(BaseResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Access denied: your account role cannot use this feature")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String message = error.getDefaultMessage();
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), message);
            } else {
                errors.put(error.getObjectName(), message);
            }
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Validation failed")
                        .errors(errors)
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Object>> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        String parameterName = ex.getName() == null ? "parameter" : ex.getName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Invalid " + parameterName + " format")
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Object>> handleMultipartLimit(
            MaxUploadSizeExceededException ex
    ) {
        boolean tooManyParts = hasCauseNamed(ex, "FileCountLimitExceededException");
        String message = tooManyParts
                ? "Too many files or multipart fields in one upload. A chapter folder supports at most 200 page images."
                : "Upload exceeds the configured request size limit.";

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(BaseResponse.builder()
                        .success(false)
                        .message(message)
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.builder()
                        .success(false)
                        .message(ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided")
                        .build());
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<BaseResponse<Object>> handleNotFoundException(Exception ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Requested resource not found"
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.builder()
                        .success(false)
                        .message(message)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled application error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Internal server error: " + ex.toString() + (ex.getCause() != null ? " | Cause: " + ex.getCause().toString() : ""))
                        .build());
    }

    private boolean hasCauseNamed(Throwable throwable, String simpleClassName) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getSimpleName().equals(simpleClassName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
