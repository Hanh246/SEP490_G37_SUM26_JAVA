package com.sep.comiverse.exception;

import com.sep.comiverse.dto.response.BaseResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OfflineDownloadExceptionHandler {

    @ExceptionHandler(OfflineDownloadException.class)
    public ResponseEntity<BaseResponse<Object>> handle(OfflineDownloadException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(BaseResponse.builder()
                        .success(false)
                        .message(exception.getMessage())
                        .errors(Map.of("code", exception.getErrorCode()))
                        .build());
    }
}
