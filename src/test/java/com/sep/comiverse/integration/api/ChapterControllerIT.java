package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChapterControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-ChapterController-001 [UC-18]: POST /chapters - missing token should be rejected")
    void createUnauthorized() throws Exception {
        postJson("/chapters", """
                {"title":"Ch 1","chapterNumber":"1"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-002 [UC-43]: POST /chapters - READER should return 403")
    void createForbidden() throws Exception {
        postJson("/chapters", """
                {"title":"Ch 1","chapterNumber":"1"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-003 [UC-18]: POST /chapters - ADMIN should return 201")
    void createAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Chapter Host Comic");
        createChapterAsAdmin(admin, comicId, "1");
    }

    @Test
    @DisplayName("TC-INT-ChapterController-004 [UC-18]: GET /chapters - missing token should be rejected")
    void listUnauthorized() throws Exception {
        getJson("/chapters").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-005 [UC-18]: GET /chapters - ADMIN should return 200")
    void listAsAdmin() throws Exception {
        getJson("/chapters", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ChapterController-006 [UC-18]: GET /chapters/{id} - ADMIN should return 200")
    void getByIdAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Get Chapter Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        getJson("/chapters/" + chapterId, admin)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-007 [UC-18]: GET /chapters/{id} - unknown id should return 404")
    void getUnknown() throws Exception {
        getJson("/chapters/" + UUID.randomUUID(), token("ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-008 [UC-18]: PUT /chapters/{id} - ADMIN should return 200")
    void updateAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Update Chapter Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        putJson("/chapters/" + chapterId, """
                {"comicId":"%s","chapterNumber":"1","title":"Renamed","images":["https://cdn.example.com/p1.png"],"moderationStatus":"PREVIEW_READY","viewCount":0,"isPremium":false,"pageCount":1}
                """.formatted(comicId), admin)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-009 [UC-18]: DELETE /chapters/{id} - ADMIN should return 200")
    void deleteAsAdmin() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Delete Chapter Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        deleteJson("/chapters/" + chapterId, admin).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-010 [UC-07]: GET /chapters/detail/{id} - unknown id should return 404")
    void detailUnknown() throws Exception {
        getJson("/chapters/detail/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-011 [UC-07]: GET /chapters/detail/{id} - unpublished chapter should be hidden")
    void unpublishedHidden() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Hidden Chapter Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        getJson("/chapters/detail/" + chapterId).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-012 [UC-07]: GET /chapters/detail/{id} - published chapter should return 200")
    void publishedDetail() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Public Chapter Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        putJson("/chapters/" + chapterId + "/approve", "{}", admin).andExpect(status().isOk());
        getJson("/chapters/detail/" + chapterId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-ChapterController-013 [UC-19]: PUT /chapters/{id}/approve - READER should return 403")
    void approveForbidden() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Approve Forbidden Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        putJson("/chapters/" + chapterId + "/approve", "{}", token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-014 [UC-19]: PUT /chapters/{id}/approve - MODERATOR should return 200")
    void approveAsModerator() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Approve Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        putJson("/chapters/" + chapterId + "/approve", "{}", token("MODERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-015 [UC-19]: POST /chapters/{id}/takedown - MODERATOR should return 200")
    void takedownAsModerator() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Takedown Comic");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        putJson("/chapters/" + chapterId + "/approve", "{}", admin).andExpect(status().isOk());
        postJson("/chapters/" + chapterId + "/takedown", "{}", token("MODERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-ChapterController-016 [UC-06]: GET /chapters/comic/{comicId} - public list should return 200")
    void listByComic() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "List By Comic");
        createChapterAsAdmin(admin, comicId, "1");
        getJson("/chapters/comic/" + comicId).andExpect(status().isOk());
    }
}
