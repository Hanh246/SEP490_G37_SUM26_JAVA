package com.sep.comiverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.CustomUserDetailsService;
import com.sep.comiverse.service.LoginDeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintWriter;

@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final ObjectMapper objectMapper;
    private final LoginDeviceService loginDeviceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);
        try {
            if (StringUtils.hasLength(jwt) && jwtTokenUtil.validateJwtToken(jwt)) {
                String userIdStr = jwtTokenUtil.getSubjectFromJwtToken(jwt);
                java.util.UUID userId = java.util.UUID.fromString(userIdStr);
                java.util.UUID loginDeviceId = jwtTokenUtil.getLoginDeviceIdFromToken(jwt);
                if (loginDeviceId != null && !loginDeviceService.isActive(userId, loginDeviceId)) {
                    throw new IllegalStateException("LOGIN_DEVICE_REVOKED");
                }
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            var error = BaseResponse.builder()
                    .success(false)
                    .message("UNAUTHORIZED: " + e.getMessage())
                    .build();
            try (PrintWriter writer = response.getWriter()) {
                writer.write(objectMapper.writeValueAsString(error));
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
