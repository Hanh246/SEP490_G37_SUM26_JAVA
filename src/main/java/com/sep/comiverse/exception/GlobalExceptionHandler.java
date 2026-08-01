package com.sep.comiverse.exception;

import com.sep.comiverse.dto.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Object>> handleCustomException(CustomException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(BaseResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Access denied: your account role cannot use this feature")
                        .build());
    }

    // Handle validation exceptions caused by @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Validation failed")
                        .errors(errors)
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Object>> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String parameterName = ex.getName() == null ? "parameter" : ex.getName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Invalid " + parameterName + " format")
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Object>> handleMultipartLimit(MaxUploadSizeExceededException ex) {
        boolean tooManyParts = hasCauseNamed(ex, "FileCountLimitExceededException");
        String message = tooManyParts
                ? "Too many files or multipart fields in one upload. A chapter folder supports at most 200 page images."
                : "Upload exceeds the 250MB request limit.";

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(BaseResponse.builder()
                        .success(false)
                        .message(message)
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

    // Handle all unforeseen system errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleGeneralException(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.builder()
                        .success(false)
                        .message("Internal server error: " + ex.getMessage())
                        .build());
    }
}
