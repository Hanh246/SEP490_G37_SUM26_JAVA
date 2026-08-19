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
    @DisplayName("TC-INT-AuthController-001 [UC-32]: POST /auth/register - valid payload should return success")
    void registerValid() throws Exception {
        String username = "r" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        postJson("/auth/register", """
                {"username":"%s","password":"Password123!","fullName":"Black Box User","email":"%s@example.com"}
                """.formatted(username, username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("verification OTP")));
    }

    @Test
    @DisplayName("TC-INT-AuthController-002 [UC-32]: POST /auth/register - blank username should return 400")
    void registerBlankUsername() throws Exception {
        postJson("/auth/register", """
                {"username":"","password":"Password123!","fullName":"Black Box User","email":"blankuser@example.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("TC-INT-AuthController-003 [UC-32]: POST /auth/register - invalid email should return 400")
    void registerInvalidEmail() throws Exception {
        postJson("/auth/register", """
                {"username":"validuser1","password":"Password123!","fullName":"Black Box User","email":"not-an-email"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-004 [UC-32]: POST /auth/register - short password should return 400")
    void registerShortPassword() throws Exception {
        postJson("/auth/register", """
                {"username":"validuser2","password":"123","fullName":"Black Box User","email":"shortpass@example.com"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-005 [UC-32]: POST /auth/register - duplicate username should return 400")
    void registerDuplicateUsername() throws Exception {
        SeededUser existing = seedUser("READER");
        postJson("/auth/register", """
                {"username":"%s","password":"Password123!","fullName":"Other User","email":"dupname@example.com"}
                """.formatted(existing.username()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-006 [UC-32]: POST /auth/register - duplicate email should return 400")
    void registerDuplicateEmail() throws Exception {
        SeededUser existing = seedUser("READER");
        postJson("/auth/register", """
                {"username":"otheruser9","password":"Password123!","fullName":"Other User","email":"%s"}
                """.formatted(existing.email()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-007 [UC-01]: POST /auth/login - valid credentials should return tokens")
    void loginValid() throws Exception {
        SeededUser reader = seedUser("READER");
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(reader.username(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AuthController-008 [UC-01]: POST /auth/login - wrong password should return 401")
    void loginWrongPassword() throws Exception {
        SeededUser reader = seedUser("READER");
        postJson("/auth/login", """
                {"username":"%s","password":"WrongPass1!"}
                """.formatted(reader.username()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-009 [UC-01]: POST /auth/login - unknown user should return 401")
    void loginUnknown() throws Exception {
        postJson("/auth/login", """
                {"username":"nobody_here","password":"Password123!"}
                """)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-010 [UC-01 / UC-04]: POST /auth/login - pending verification should return 403")
    void loginPending() throws Exception {
        SeededUser pending = seedUser("READER", "PENDING_VERIFICATION");
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(pending.username(), PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-011 [UC-01]: POST /auth/login - banned account should return 403")
    void loginBanned() throws Exception {
        SeededUser banned = seedUser("READER", "INACTIVE");
        postJson("/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(banned.username(), PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-012 [UC-03]: GET /auth/me - missing token should return 401")
    void meUnauthorized() throws Exception {
        getJson("/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-AuthController-013 [UC-03]: GET /auth/me - valid token should return the profile")
    void meAuthorized() throws Exception {
        SeededUser reader = seedUser("READER");
        getJson("/auth/me", login(reader.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(reader.username()));
    }

    @Test
    @DisplayName("TC-INT-AuthController-014 [UC-03]: POST /auth/change-password - missing token should be rejected")
    void changePasswordUnauthorized() throws Exception {
        postJson("/auth/change-password", """
                {"currentPassword":"Password123!","newPassword":"NewPass123!"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthController-015 [UC-03]: POST /auth/change-password - wrong current password should return 400")
    void changePasswordWrongCurrent() throws Exception {
        postJson("/auth/change-password", """
                {"currentPassword":"WrongOld1!","newPassword":"NewPass123!"}
                """, token("READER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-016 [UC-03]: POST /auth/change-password - valid payload should return success")
    void changePasswordValid() throws Exception {
        SeededUser reader = seedUser("READER");
        postJson("/auth/change-password", """
                {"currentPassword":"%s","newPassword":"NewPass123!"}
                """.formatted(PASSWORD), login(reader.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-017 [UC-03]: PUT /auth/profile - missing token should return 403")
    void profileUnauthorized() throws Exception {
        putJson("/auth/profile", """
                {"fullName":"Updated Name"}
                """, null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthController-018 [UC-03]: PUT /auth/profile - valid payload should update the profile")
    void profileValid() throws Exception {
        putJson("/auth/profile", """
                {"fullName":"Updated Black Box"}
                """, token("READER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-019 [UC-45]: POST /auth/forgot-password - unknown email should still return success")
    void forgotUnknownEmail() throws Exception {
        postJson("/auth/forgot-password", """
                {"email":"missing@example.com"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AuthController-020 [UC-45]: POST /auth/reset-password - invalid OTP should return 400")
    void resetInvalidOtp() throws Exception {
        SeededUser reader = seedUser("READER");
        postJson("/auth/reset-password", """
                {"email":"%s","otp":"000000","newPassword":"NewPass123!"}
                """.formatted(reader.email()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-021 [UC-04]: POST /auth/verify-email - invalid OTP should return 400")
    void verifyInvalidOtp() throws Exception {
        postJson("/auth/verify-email", """
                {"email":"anyone@example.com","otp":"000000"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthController-022 [UC-37]: POST /auth/register-staff - READER should return 403")
    void registerStaffForbidden() throws Exception {
        postJson("/auth/register-staff", """
                {"username":"staffone","password":"Password123!","fullName":"Staff","email":"staffone@example.com","role":"MODERATOR"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }
}
