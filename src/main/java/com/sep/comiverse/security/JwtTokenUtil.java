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
import java.util.UUID;

@Component
public class JwtTokenUtil {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final long LEGACY_TOKEN_TOLERANCE_MS = 60_000L;

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
        return generateToken(user, null);
    }

    public String generateToken(com.sep.comiverse.entity.UserEntity user, UUID loginDeviceId) {
        Instant expiration = Instant.now().plusMillis(jwtExpirationMs);
        return buildToken(user, expiration, loginDeviceId, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(com.sep.comiverse.entity.UserEntity user) {
        return generateRefreshToken(user, null);
    }

    public String generateRefreshToken(com.sep.comiverse.entity.UserEntity user, UUID loginDeviceId) {
        long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;
        Instant expiration = Instant.now().plusMillis(refreshExpirationMs);
        return buildToken(user, expiration, loginDeviceId, REFRESH_TOKEN_TYPE);
    }

    private String buildToken(
            com.sep.comiverse.entity.UserEntity user,
            Instant expiration,
            UUID loginDeviceId,
            String tokenType
    ) {
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole() != null ? user.getRole().getRoleName() : "READER")
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration));
        if (loginDeviceId != null) {
            builder.claim("loginDeviceId", loginDeviceId.toString());
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
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

    public UUID getLoginDeviceIdFromToken(String token) {
        Object value = getClaimsFromToken(token).get("loginDeviceId");
        if (value == null || value.toString().isBlank()) return null;
        return UUID.fromString(value.toString());
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(resolveTokenType(getClaimsFromToken(token)));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(resolveTokenType(getClaimsFromToken(token)));
    }

    private String resolveTokenType(io.jsonwebtoken.Claims claims) {
        Object explicitType = claims.get(TOKEN_TYPE_CLAIM);
        if (explicitType != null && !explicitType.toString().isBlank()) {
            return explicitType.toString().trim().toUpperCase(java.util.Locale.ROOT);
        }

        // Tokens issued before tokenType was introduced are classified by their
        // lifetime so existing access sessions keep working after deployment.
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        if (issuedAt != null && expiration != null) {
            long lifetimeMs = expiration.getTime() - issuedAt.getTime();
            if (lifetimeMs > jwtExpirationMs + LEGACY_TOKEN_TOLERANCE_MS) {
                return REFRESH_TOKEN_TYPE;
            }
        }
        return ACCESS_TOKEN_TYPE;
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

    public UUID getCurrentUserId() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
