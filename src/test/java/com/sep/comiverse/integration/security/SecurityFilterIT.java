package com.sep.comiverse.integration.security;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterIT extends AbstractBlackboxIT {

    // ── NFR-20: Token expiration ─────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-001 [NFR-20]: Expired JWT should return 401 Unauthorized")
    void expiredJwt() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(
                "test-jwt-secret-for-comiverse-context-loads-1234567890".getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject(seedUser("READER").id().toString())
                .claim("role", "READER")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + jwt)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-002 [NFR-20]: JWT signed with wrong secret should return 401 Unauthorized")
    void wrongSecret() throws Exception {
        SecretKey other = Keys.hmacShaKeyFor(
                "wrong-secret-for-comiverse-tests-1234567890xxxx".getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject(seedUser("ADMIN").id().toString())
                .claim("role", "ADMIN")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(other, Jwts.SIG.HS256)
                .compact();
        mockMvc.perform(get("/admin/settings/premium-plans")
                        .header("Authorization", "Bearer " + jwt)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-003 [NFR-20]: Malformed JWT should return 401 Unauthorized")
    void malformedJwt() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer not.a.valid.token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-004 [NFR-20]: Empty Bearer token should return 401 Unauthorized")
    void emptyBearer() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-005 [NFR-20]: Token without Bearer prefix should return 401 Unauthorized")
    void missingBearerPrefix() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", token("READER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-03: Protected resources require authentication ───────────────────

    @Test
    @DisplayName("TC-SEC-006 [NFR-03]: Protected endpoint without token should return 401 Unauthorized")
    void protectedWithoutToken() throws Exception {
        getJson("/admin/settings/premium-plans").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-007 [NFR-03]: READER accessing ADMIN endpoint should return 403 Forbidden")
    void readerForbiddenOnAdmin() throws Exception {
        getJson("/admin/settings/premium-plans", token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-008 [NFR-03]: ADMIN token can access protected admin endpoint")
    void adminCanAccessAdmin() throws Exception {
        getJson("/admin/settings/premium-plans", token("ADMIN"))
                .andExpect(status().isOk());
    }

    // ── NFR-10: Workflow enforcement (role-based access) ─────────────────────

    @Test
    @DisplayName("TC-SEC-009 [NFR-10]: READER cannot POST /chapters (bypass workflow)")
    void readerCannotCreateChapter() throws Exception {
        postJson("/chapters", """
                {"title":"Ch","chapterNumber":"1","comicId":"00000000-0000-0000-0000-000000000000"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-010 [NFR-10]: TRANSLATOR cannot approve a submission")
    void translatorCannotApproveSubmission() throws Exception {
        putJson("/submissions/00000000-0000-0000-0000-000000000001/approve", "{}", token("TRANSLATOR"))
                .andExpect(status().isForbidden());
    }

    // ── NFR-23: File upload constraints ─────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-011 [NFR-23]: Upload endpoint without token should return 401 Unauthorized")
    void uploadWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.png", "image/png", "img".getBytes());
        mockMvc.perform(multipart("/upload/image").file(file))
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-25: Stripe webhook on public whitelist ───────────────────────────

    @Test
    @DisplayName("TC-SEC-012 [NFR-25]: Stripe webhook should not return 401 (public whitelist)")
    void stripeWebhookPublic() throws Exception {
        mockMvc.perform(post("/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    if (result.getResponse().getStatus() == 401) {
                        throw new AssertionError(
                                "POST /stripe/webhook must be on the public whitelist");
                    }
                });
    }

    // ── NFR-29: WebSocket / subscription protected ──────────────────────────

    @Test
    @DisplayName("TC-SEC-013 [NFR-29]: POST /comics without token should return 401 Unauthorized")
    void createComicWithoutToken() throws Exception {
        postJson("/comics", """
                {"title":"X","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-32: Page-size guard ──────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-014 [NFR-32]: GET /comics with size above 100 should return 400 Bad Request")
    void oversizedPage() throws Exception {
        getJson("/comics?size=200").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-SEC-015 [NFR-32]: GET /comics with page below 1 should return 400 Bad Request")
    void invalidPageNumber() throws Exception {
        getJson("/comics?page=0").andExpect(status().isBadRequest());
    }

    // ── CORS (cross-cutting security) ────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-016 [NFR-33]: Allowed origin preflight should return CORS headers")
    void corsAllowedOrigin() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://comi-verse.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "https://comi-verse.vercel.app"));
    }

    @Test
    @DisplayName("TC-SEC-017 [NFR-33]: Disallowed origin should not be echoed in CORS response")
    void corsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(result -> {
                    String allow = result.getResponse()
                            .getHeader("Access-Control-Allow-Origin");
                    if ("https://evil.example.com".equals(allow)) {
                        throw new AssertionError("Disallowed origin must not be echoed");
                    }
                });
    }

    // ── Public whitelist ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-018 [NFR-03]: GET /comics should remain on public whitelist")
    void comicsPublic() throws Exception {
        getJson("/comics").andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-019 [NFR-03]: GET /v3/api-docs should remain publicly accessible")
    void openApiPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ── CSRF disabled ────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-020 [NFR-33]: POST /auth/login should succeed without CSRF token")
    void csrfDisabled() throws Exception {
        SeededUser reader = seedUser("READER");
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(reader.username(), PASSWORD))
                .andExpect(status().isOk());
    }
}
