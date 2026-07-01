package com.sep.comiverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.CustomUserDetailsService;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);
        try {
            if (StringUtils.hasLength(jwt) && jwtTokenUtil.validateJwtToken(jwt)) {
                // Read claims directly from token without DB lookup
                io.jsonwebtoken.Claims claims = jwtTokenUtil.getClaimsFromToken(jwt);
                String userId = claims.getSubject();

                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);
                String fullName = claims.get("fullName", String.class);

                // Construct temp user entity and principal
                com.sep.comiverse.entity.RoleEntity roleEntity = com.sep.comiverse.entity.RoleEntity.builder()
                        .roleName(role)
                        .build();

                com.sep.comiverse.entity.UserEntity tempUser = new com.sep.comiverse.entity.UserEntity();
                tempUser.setId(java.util.UUID.fromString(userId));
                tempUser.setEmail(email);
                tempUser.setFullName(fullName);
                tempUser.setRole(roleEntity);
                tempUser.setStatus("ACTIVE");

                UserDetails userDetails = new UserPrincipal(tempUser);

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
