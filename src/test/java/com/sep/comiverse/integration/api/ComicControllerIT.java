package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComicControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-ComicController-001")
    void listPublished() throws Exception {
        getJson("/comics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-002")
    void listInvalidPage() throws Exception {
        getJson("/comics?page=0").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-ComicController-003")
    void listInvalidSize() throws Exception {
        getJson("/comics?size=101").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-ComicController-004")
    void listAll() throws Exception {
        getJson("/comics/all")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-005")
    void explore() throws Exception {
        getJson("/comics/explore")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-006")
    void leaderboard() throws Exception {
        getJson("/comics/leaderboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-007")
    void recommendations() throws Exception {
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-008")
    void unknownDetail() throws Exception {
        getJson("/comics/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-ComicController-009")
    void detailAfterCreate() throws Exception {
        // author_test's author entity is pre-provisioned; look up its user id
        SeededUser authorSeed = fixedUser(AUTHOR_USER);
        UUID comicId = createComicAsAdmin(fixedToken(ADMIN_USER), authorSeed.id(), "Public Comic One");
        getJson("/comics/" + comicId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(comicId.toString()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-010")
    void createUnauthorized() throws Exception {
        postJson("/comics", """
                {"title":"No Auth Comic","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ComicController-011")
    void createForbidden() throws Exception {
        postJson("/comics", """
                {"title":"Reader Comic","language":"en","cover":"https://cdn.example.com/c.png"}
                """, fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    
}
