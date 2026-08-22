package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-AuthController-001 [UC-32]")
    void registerValid() throws Exception {
        String username = "r" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        postJson("/auth/register", """
                {"username":"%s","password":"Test@1234","fullName":"Black Box User","email":"%s@example.com"}
                """.formatted(username, username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("verification OTP")));
    }

    @Test
    @DisplayName("TC-INT-AuthController-002 [UC-32]")
    void registerBlankUsername() throws Exception {
        postJson("/auth/register", """
                {"username":"","password":"Test@1234","fullName":"Black Box User","email":"blankuser@example.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("TC-INT-AuthController-003 [UC-32]")
    void registerInvalidEmail() throws Exception {
        postJson("/auth/register", """
                {"username":"validuser1","password":"Test@1234","fullName":"Black Box User","email":"not-an-email"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-004 [UC-32]")
    void registerShortPassword() throws Exception {
        postJson("/auth/register", """
                {"username":"validuser2","password":"123","fullName":"Black Box User","email":"shortpass@example.com"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-005 [UC-32]")
    void registerDuplicateUsername() throws Exception {
        SeededUser reader = fixedUser(READER_USER);
        postJson("/auth/register", """
                {"username":"%s","password":"%s","fullName":"Other User","email":"dupname@example.com"}
                """.formatted(reader.username(), FIXED_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-006 [UC-32]")
    void registerDuplicateEmail() throws Exception {
        SeededUser reader = fixedUser(READER_USER);
        // Username must be new; colliding on reader_test's email is the case under test.
        postJson("/auth/register", """
                {"username":"new_reader_dup","password":"%s","fullName":"Other User","email":"%s"}
                """.formatted(FIXED_PASSWORD, reader.email()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-007 [UC-01]")
    void loginValid() throws Exception {
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(READER_USER, FIXED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AuthController-008 [UC-01]")
    void loginWrongPassword() throws Exception {
        postJson("/auth/login", """
                {"username":"%s","password":"WrongPass1!"}
                """.formatted(READER_USER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-009 [UC-01]")
    void loginUnknown() throws Exception {
        postJson("/auth/login", """
                {"username":"nobody_here","password":"Test@1234"}
                """)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-010 [UC-01 / UC-04]")
    void loginPending() throws Exception {
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(PENDING_USER, FIXED_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-011 [UC-01]")
    void loginBanned() throws Exception {
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(BANNED_USER, FIXED_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-012 [UC-03]")
    void meUnauthorized() throws Exception {
        getJson("/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-013 [UC-03]")
    void meAuthorized() throws Exception {
        getJson("/auth/me", fixedToken(READER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(READER_USER));
    }

    @Test
    @DisplayName("TC-INT-AuthController-014 [UC-03]")
    void changePasswordUnauthorized() throws Exception {
        postJson("/auth/change-password", """
                {"currentPassword":"Test@1234","newPassword":"NewPass123!"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthController-015 [UC-03]")
    void changePasswordWrongCurrent() throws Exception {
        postJson("/auth/change-password", """
                {"currentPassword":"WrongOld1!","newPassword":"NewPass123!"}
                """, fixedToken(READER_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-016 [UC-03]")
    void changePasswordValid() throws Exception {
        postJson("/auth/change-password", """
                {"currentPassword":"%s","newPassword":"NewPass123!"}
                """.formatted(FIXED_PASSWORD), fixedToken(READER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-017 [UC-03]")
    void profileUnauthorized() throws Exception {
        putJson("/auth/profile", """
                {"fullName":"Updated Name"}
                """, null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-018 [UC-03]")
    void profileValid() throws Exception {
        putJson("/auth/profile", """
                {"fullName":"Updated Black Box"}
                """, fixedToken(READER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-019 [UC-45]")
    void forgotUnknownEmail() throws Exception {
        postJson("/auth/forgot-password", """
                {"email":"missing@example.com"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-020 [UC-45]")
    void resetInvalidOtp() throws Exception {
        postJson("/auth/reset-password", """
                {"email":"%s@example.com","otp":"000000","newPassword":"NewPass123!"}
                """.formatted(READER_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-021 [UC-04]")
    void verifyInvalidOtp() throws Exception {
        postJson("/auth/verify-email", """
                {"email":"anyone@example.com","otp":"000000"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-022 [UC-37]")
    void registerStaffForbidden() throws Exception {
        postJson("/auth/register-staff", """
                {"username":"staffone","password":"Test@1234","fullName":"Staff","email":"staffone@example.com","role":"MODERATOR"}
                """, fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }
}
