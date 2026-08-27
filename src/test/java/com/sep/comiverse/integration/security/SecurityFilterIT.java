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
    @DisplayName("TC-SEC-001")
    void expiredJwt() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(
                "test-jwt-secret-for-comiverse-context-loads-1234567890".getBytes(StandardCharsets.UTF_8));
        // reader_test is a pre-provisioned fixed account — its id is not needed for JWT subject validation
        String jwt = Jwts.builder()
                .subject(fixedUser(READER_USER).id().toString())
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
    @DisplayName("TC-SEC-002")
    void wrongSecret() throws Exception {
        SecretKey other = Keys.hmacShaKeyFor(
                "wrong-secret-for-comiverse-tests-1234567890xxxx".getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject(fixedUser(ADMIN_USER).id().toString())
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
    @DisplayName("TC-SEC-003")
    void malformedJwt() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer not.a.valid.token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-004")
    void emptyBearer() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-005")
    void missingBearerPrefix() throws Exception {
        // Pass the raw JWT without the "Bearer " prefix — must be rejected
        String rawJwt = fixedToken(READER_USER);
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", rawJwt)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-03: Protected resources require authentication ───────────────────

    @Test
    @DisplayName("TC-SEC-006")
    void protectedWithoutToken() throws Exception {
        getJson("/admin/settings/premium-plans").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-007")
    void readerForbiddenOnAdmin() throws Exception {
        getJson("/admin/settings/premium-plans", fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-008")
    void adminCanAccessAdmin() throws Exception {
        getJson("/admin/settings/premium-plans", fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    // ── NFR-10: Workflow enforcement (role-based access) ─────────────────────

    @Test
    @DisplayName("TC-SEC-009")
    void readerCannotCreateChapter() throws Exception {
        postJson("/chapters", """
                {"title":"Ch","chapterNumber":"1","comicId":"00000000-0000-0000-0000-000000000000"}
                """, fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-010")
    void translatorCannotApproveSubmission() throws Exception {
        putJson("/submissions/00000000-0000-0000-0000-000000000001/approve", "{}", fixedToken(TRANS_USER))
                .andExpect(status().isForbidden());
    }

    // ── NFR-23: File upload constraints ─────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-011")
    void uploadWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.png", "image/png", "img".getBytes());
        mockMvc.perform(multipart("/upload/image").file(file))
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-25: Stripe webhook on public whitelist ───────────────────────────

    @Test
    @DisplayName("TC-SEC-012")
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
    @DisplayName("TC-SEC-013")
    void createComicWithoutToken() throws Exception {
        postJson("/comics", """
                {"title":"X","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().isUnauthorized());
    }

    // ── NFR-32: Page-size guard ──────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-014")
    void oversizedPage() throws Exception {
        getJson("/comics?size=200").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-SEC-015")
    void invalidPageNumber() throws Exception {
        getJson("/comics?page=0").andExpect(status().isBadRequest());
    }

    // ── CORS (cross-cutting security) ────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-016")
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
    @DisplayName("TC-SEC-017")
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
    @DisplayName("TC-SEC-018")
    void comicsPublic() throws Exception {
        getJson("/comics").andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-019")
    void openApiPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-021")
    void emailTestEndpointIsNotPublic() throws Exception {
        postJson("/test/email/util", """
                {"to":"reader@example.com","subject":"test","content":"test"}
                """)
                .andExpect(status().isUnauthorized());
    }

    // ── CSRF disabled ────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-SEC-020")
    void csrfDisabled() throws Exception {
        // reader_test is pre-provisioned — login should succeed without any CSRF token header
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(READER_USER, FIXED_PASSWORD))
                .andExpect(status().isOk());
    }
}
