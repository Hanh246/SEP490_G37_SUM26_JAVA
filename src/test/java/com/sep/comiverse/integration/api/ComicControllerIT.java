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
    @DisplayName("TC-INT-ComicController-001 [UC-05]: GET /comics - public catalog should return 200")
    void listPublished() throws Exception {
        getJson("/comics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-002 [UC-05]: GET /comics - page below 1 should return 400")
    void listInvalidPage() throws Exception {
        getJson("/comics?page=0").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-ComicController-003 [UC-05]: GET /comics - size above 100 should return 400")
    void listInvalidSize() throws Exception {
        getJson("/comics?size=101").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-ComicController-004 [UC-05]: GET /comics/all - catalog should return 200")
    void listAll() throws Exception {
        getJson("/comics/all")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-005 [UC-05]: GET /comics/explore - explore feed should return 200")
    void explore() throws Exception {
        getJson("/comics/explore")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-006 [UC-14]: GET /comics/leaderboard - rankings should return 200")
    void leaderboard() throws Exception {
        getJson("/comics/leaderboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-007 [UC-14]: GET /comics/recommendations - personalized feed should return 200")
    void recommendations() throws Exception {
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-008 [UC-06]: GET /comics/{id} - unknown id should return 404")
    void unknownDetail() throws Exception {
        getJson("/comics/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-ComicController-009 [UC-06]: GET /comics/{id} - published comic should return 200")
    void detailAfterCreate() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        UUID comicId = createComicAsAdmin(token("ADMIN"), author.id(), "Public Comic One");
        getJson("/comics/" + comicId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(comicId.toString()));
    }

    @Test
    @DisplayName("TC-INT-ComicController-010 [UC-43]: POST /comics - missing token should be rejected")
    void createUnauthorized() throws Exception {
        postJson("/comics", """
                {"title":"No Auth Comic","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ComicController-011 [UC-43]: POST /comics - READER should return 403")
    void createForbidden() throws Exception {
        postJson("/comics", """
                {"title":"Reader Comic","language":"en","cover":"https://cdn.example.com/c.png"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ComicController-012 [UC-43]: POST /comics - ADMIN should return 201")
    void createAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        UUID id = createComicAsAdmin(token("ADMIN"), author.id(), "Admin Created Comic");
        getJson("/comics/" + id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ComicController-013 [UC-43]: PUT /comics/{id} - READER should return 403")
    void updateForbidden() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        UUID comicId = createComicAsAdmin(token("ADMIN"), author.id(), "Update Forbidden Comic");
        putJson("/comics/" + comicId, """
                {"title":"Hacked","language":"en","cover":"https://cdn.example.com/c.png"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ComicController-014 [UC-43]: PUT /comics/{id} - ADMIN should return 200")
    void updateAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Update Me Comic");
        putJson("/comics/" + comicId, """
                {"title":"Updated Title","language":"en","cover":"https://cdn.example.com/c.png","summary":"Updated","authorId":"%s","chapterCount":0,"viewCount":0,"likeCount":0,"saveCount":0,"ratingAverage":0,"ratingCount":0,"moderationStatus":"DRAFT","publicationStatus":"ONGOING","isAppealed":false,"isModEdited":false}
                """.formatted(author.id()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ComicController-015 [UC-43]: DELETE /comics/{id} - READER should return 403")
    void deleteForbidden() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        UUID comicId = createComicAsAdmin(token("ADMIN"), author.id(), "Delete Forbidden Comic");
        deleteJson("/comics/" + comicId, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ComicController-016 [UC-43]: DELETE /comics/{id} - ADMIN should return 200")
    void deleteAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Delete Me Comic");
        deleteJson("/comics/" + comicId, admin)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ComicController-017 [UC-43]: GET /comics/staff/all - READER should return 403")
    void staffAllForbidden() throws Exception {
        getJson("/comics/staff/all", token("READER")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ComicController-018 [UC-43]: GET /comics/staff/all - ADMIN should return 200")
    void staffAllAdmin() throws Exception {
        getJson("/comics/staff/all", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
