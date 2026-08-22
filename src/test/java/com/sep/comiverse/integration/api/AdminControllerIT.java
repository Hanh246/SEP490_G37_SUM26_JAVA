package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-AdminController-001 [UC-37]")
    void listUnauthorized() throws Exception {
        getJson("/admin/users").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AdminController-002 [UC-37]")
    void listForbidden() throws Exception {
        getJson("/admin/users", fixedToken(READER_USER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AdminController-003 [UC-37]")
    void listAsAdmin() throws Exception {
        getJson("/admin/users", fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AdminController-004 [UC-37]")
    void filterByRole() throws Exception {
        getJson("/admin/users?role=READER", fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AdminController-005 [UC-37]")
    void invalidPage() throws Exception {
        getJson("/admin/users?page=0", fixedToken(ADMIN_USER)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AdminController-006 [UC-37]")
    void getById() throws Exception {
        // Use a seeded user as the lookup target (fixed accounts may have @Transactional rollback timing)
        SeededUser target = fixedUser(READER_USER);
        getJson("/admin/users/" + target.id(), fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(target.email()));
    }

    @Test
    @DisplayName("TC-INT-AdminController-007 [UC-37]")
    void getUnknown() throws Exception {
        getJson("/admin/users/" + java.util.UUID.randomUUID(), fixedToken(ADMIN_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-AdminController-008 [UC-37]")
    void banForbidden() throws Exception {
        SeededUser target = fixedUser(READER_USER);
        putJson("/admin/users/" + target.id() + "/ban", "{}", fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AdminController-009 [UC-37]")
    void banAsAdmin() throws Exception {
        SeededUser target = fixedUser(READER_USER);
        putJson("/admin/users/" + target.id() + "/ban", "{}", fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AdminController-010 [UC-37]")
    void cannotBanAdmin() throws Exception {
        SeededUser otherAdmin = fixedUser(ADMIN_USER);
        putJson("/admin/users/" + otherAdmin.id() + "/ban", "{}", fixedToken(ADMIN_USER))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AdminController-011 [UC-37]")
    void unbanAsAdmin() throws Exception {
        SeededUser target = fixedUser(BANNED_USER);
        putJson("/admin/users/" + target.id() + "/unban", "{}", fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-012 [UC-37]")
    void resetPassword() throws Exception {
        SeededUser target = fixedUser(READER_USER);
        postJson("/admin/users/" + target.id() + "/reset-password", "{}", fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-013 [UC-37]")
    void updateUser() throws Exception {
        SeededUser target = fixedUser(READER_USER);
        putJson("/admin/users/" + target.id(), """
                {"fullName":"Renamed Reader","role":"READER"}
                """, fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-014 [UC-37]")
    void updateBlankName() throws Exception {
        SeededUser target = fixedUser(READER_USER);
        putJson("/admin/users/" + target.id(), """
                {"fullName":""}
                """, fixedToken(ADMIN_USER))
                .andExpect(status().isBadRequest());
    }
}
