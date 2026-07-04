package com.sep.comiverse.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import com.sep.comiverse.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${app.auth.tokenSecret}")
    private String jwtSecret;

    @Value("${app.auth.tokenExpirationMs}")
    private Long jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(com.sep.comiverse.entity.UserEntity user) {
        Instant expiration = Instant.now().plusMillis(jwtExpirationMs);
        return buildToken(user, expiration);
    }

    public String generateRefreshToken(com.sep.comiverse.entity.UserEntity user) {
        long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;
        Instant expiration = Instant.now().plusMillis(refreshExpirationMs);
        return buildToken(user, expiration);
    }

    private String buildToken(com.sep.comiverse.entity.UserEntity user, Instant expiration) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole() != null ? user.getRole().getRoleName() : "READER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public io.jsonwebtoken.Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getSubjectFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(authToken);
            return true;
        } catch (SecurityException | SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
            throw new CustomException(401, "SIGNATURE_NOT_CORRECT", HttpStatus.UNAUTHORIZED);
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            throw new CustomException(401, "AUTHORIZATION_FIELD_MISSING", HttpStatus.UNAUTHORIZED);
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
            throw new CustomException(401, "TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED);
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
            throw new CustomException(401, "UNSUPPORTED", HttpStatus.UNAUTHORIZED);
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
            throw new CustomException(401, "ILLEGAL_ARGUMENT", HttpStatus.UNAUTHORIZED);
        }
    }
}
