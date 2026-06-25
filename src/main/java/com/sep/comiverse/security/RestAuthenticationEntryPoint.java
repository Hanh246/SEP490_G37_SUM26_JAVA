package com.sep.comiverse.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger logger = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest httpServletRequest,
                          HttpServletResponse httpServletResponse,
                          AuthenticationException e) throws IOException {
        logger.error("Responding with unauthorized error. Message - {}", e.getMessage());
        httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        var error = BaseResponse.builder()
                .success(false)
                .message("UNAUTHORIZED: " + e.getMessage())
                .build();

        try (PrintWriter writer = httpServletResponse.getWriter()) {
            writer.write(objectMapper.writeValueAsString(error));
        }
    }
}
