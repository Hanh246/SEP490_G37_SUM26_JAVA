package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorComicControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-AuthorComicController-001 [UC-17]: POST /author/comics - missing token should be rejected")
    void createUnauthorized() throws Exception {
        postJson("/author/comics", """
                {"title":"Draft","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-002 [UC-17]: POST /author/comics - READER should return 403")
    void createForbidden() throws Exception {
        postJson("/author/comics", """
                {"title":"Draft","language":"en","cover":"https://cdn.example.com/c.png"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-003 [UC-17]: POST /author/comics - licensed AUTHOR should return 201")
    void createAsAuthor() throws Exception {
        createAuthorComic(token("AUTHOR"), "Author Draft One");
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-004 [UC-17]: POST /author/comics - missing title should return 400")
    void missingTitle() throws Exception {
        postJson("/author/comics", """
                {"language":"en","cover":"https://cdn.example.com/c.png"}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-005 [UC-17]: POST /author/comics - missing cover should return 400")
    void missingCover() throws Exception {
        postJson("/author/comics", """
                {"title":"No Cover","language":"en"}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-006 [UC-17]: POST /author/comics - minimumAge above 21 should return 400")
    void minAgeTooHigh() throws Exception {
        postJson("/author/comics", """
                {"title":"Adult","language":"en","cover":"https://cdn.example.com/c.png","minimumAge":30}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-007 [UC-17]: GET /author/comics - AUTHOR should return 200")
    void listOwn() throws Exception {
        String token = token("AUTHOR");
        createAuthorComic(token, "Listed Draft");
        getJson("/author/comics", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-008 [UC-17]: GET /author/comics/{id} - owner should return 200")
    void getOwn() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Owned Draft");
        getJson("/author/comics/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-009 [UC-17]: GET /author/comics/{id} - another author should return 404")
    void getForeign() throws Exception {
        UUID id = createAuthorComic(token("AUTHOR"), "Foreign Draft");
        getJson("/author/comics/" + id, token("AUTHOR")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-010 [UC-17]: PUT /author/comics/{id} - owner should return 200")
    void updateOwn() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Edit Me");
        putJson("/author/comics/" + id, """
                {"title":"Edited Draft","language":"en","cover":"https://cdn.example.com/c.png","summary":"Edited"}
                """, token)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-011 [UC-17]: PUT /author/comics/{id} - READER should return 403")
    void updateForbidden() throws Exception {
        UUID id = createAuthorComic(token("AUTHOR"), "Reader Edit");
        putJson("/author/comics/" + id, """
                {"title":"Hacked","language":"en","cover":"https://cdn.example.com/c.png"}
                """, token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-012 [UC-17]: DELETE /author/comics/{id} - owner should return 200")
    void deleteOwn() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Delete Me");
        deleteJson("/author/comics/" + id, token).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-013 [UC-19]: POST /author/comics/{id}/submit-review - no chapters should return 400")
    void submitWithoutChapters() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Empty Review");
        postJson("/author/comics/" + id + "/submit-review", "{}", token)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-014 [UC-19]: POST /author/comics/{id}/submit-review - with a chapter should return 200")
    void submitWithChapter() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String authorToken = login(author.username());
        String admin = token("ADMIN");
        UUID comicId = createAuthorComic(authorToken, "Ready Review");
        createChapterAsAdmin(admin, comicId, "1");
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-015 [UC-19]: POST /author/comics/{id}/submit-review - second submit should return 409")
    void submitTwice() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String authorToken = login(author.username());
        String admin = token("ADMIN");
        UUID comicId = createAuthorComic(authorToken, "Double Review");
        createChapterAsAdmin(admin, comicId, "1");
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken).andExpect(status().isOk());
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-016 [UC-54]: POST /author/comics/{id}/appeal - too short reason should return 400")
    void appealTooShort() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Short Appeal");
        postJson("/author/comics/" + id + "/appeal", """
                {"reason":"no"}
                """, token)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-017 [UC-54]: POST /author/comics/{id}/appeal - valid reason should return 200")
    void appealValid() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Valid Appeal");
        postJson("/author/comics/" + id + "/appeal", """
                {"reason":"The moderation notes were applied and the pages were redrawn."}
                """, token)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-018 [UC-17]: PUT /author/comics/{id}/confirm-edit - owner should return 200")
    void confirmEdit() throws Exception {
        String token = token("AUTHOR");
        UUID id = createAuthorComic(token, "Confirm Edit");
        putJson("/author/comics/" + id + "/confirm-edit", "{}", token)
                .andExpect(status().isOk());
    }
}
