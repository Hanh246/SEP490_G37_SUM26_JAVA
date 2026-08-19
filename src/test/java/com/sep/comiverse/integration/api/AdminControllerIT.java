package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-AdminController-001 [UC-37]: GET /admin/users - missing token should be rejected")
    void listUnauthorized() throws Exception {
        getJson("/admin/users").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AdminController-002 [UC-37]: GET /admin/users - READER should return 403")
    void listForbidden() throws Exception {
        getJson("/admin/users", token("READER")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AdminController-003 [UC-37]: GET /admin/users - ADMIN should return 200")
    void listAsAdmin() throws Exception {
        getJson("/admin/users", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AdminController-004 [UC-37]: GET /admin/users - role filter should return 200")
    void filterByRole() throws Exception {
        getJson("/admin/users?role=READER", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AdminController-005 [UC-37]: GET /admin/users - invalid page should return 400")
    void invalidPage() throws Exception {
        getJson("/admin/users?page=0", token("ADMIN")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AdminController-006 [UC-37]: GET /admin/users/{id} - ADMIN should return 200")
    void getById() throws Exception {
        SeededUser target = seedUser("READER");
        getJson("/admin/users/" + target.id(), token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(target.email()));
    }

    @Test
    @DisplayName("TC-INT-AdminController-007 [UC-37]: GET /admin/users/{id} - unknown id should return 404")
    void getUnknown() throws Exception {
        getJson("/admin/users/" + java.util.UUID.randomUUID(), token("ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-AdminController-008 [UC-37]: PUT /admin/users/{id}/ban - READER should return 403")
    void banForbidden() throws Exception {
        SeededUser target = seedUser("READER");
        putJson("/admin/users/" + target.id() + "/ban", "{}", token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AdminController-009 [UC-37]: PUT /admin/users/{id}/ban - ADMIN should return 200")
    void banAsAdmin() throws Exception {
        SeededUser target = seedUser("READER");
        putJson("/admin/users/" + target.id() + "/ban", "{}", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-AdminController-010 [UC-37]: PUT /admin/users/{id}/ban - admin account should be rejected")
    void cannotBanAdmin() throws Exception {
        SeededUser otherAdmin = seedUser("ADMIN");
        putJson("/admin/users/" + otherAdmin.id() + "/ban", "{}", token("ADMIN"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AdminController-011 [UC-37]: PUT /admin/users/{id}/unban - ADMIN should return 200")
    void unbanAsAdmin() throws Exception {
        SeededUser target = seedUser("READER", "INACTIVE");
        putJson("/admin/users/" + target.id() + "/unban", "{}", token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-012 [UC-37]: POST /admin/users/{id}/reset-password - ADMIN should return 200")
    void resetPassword() throws Exception {
        SeededUser target = seedUser("READER");
        postJson("/admin/users/" + target.id() + "/reset-password", "{}", token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-013 [UC-37]: PUT /admin/users/{id} - ADMIN should update the user")
    void updateUser() throws Exception {
        SeededUser target = seedUser("READER");
        putJson("/admin/users/" + target.id(), """
                {"fullName":"Renamed Reader","role":"READER"}
                """, token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AdminController-014 [UC-37]: PUT /admin/users/{id} - blank full name should return 400")
    void updateBlankName() throws Exception {
        SeededUser target = seedUser("READER");
        putJson("/admin/users/" + target.id(), """
                {"fullName":""}
                """, token("ADMIN"))
                .andExpect(status().isBadRequest());
    }
}
