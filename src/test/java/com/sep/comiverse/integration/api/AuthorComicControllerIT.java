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
    @DisplayName("TC-INT-AuthorComicController-001 [UC-17]")
    void createUnauthorized() throws Exception {
        postJson("/author/comics", """
                {"title":"Draft","language":"en","cover":"https://cdn.example.com/c.png"}
                """)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-002 [UC-17]")
    void createForbidden() throws Exception {
        postJson("/author/comics", """
                {"title":"Draft","language":"en","cover":"https://cdn.example.com/c.png"}
                """, fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-003 [UC-17]")
    void createAsAuthor() throws Exception {
        createAuthorComic(fixedToken(AUTHOR_USER), "Author Draft One");
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-004 [UC-17]")
    void missingTitle() throws Exception {
        postJson("/author/comics", """
                {"language":"en","cover":"https://cdn.example.com/c.png"}
                """, fixedToken(AUTHOR_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-005 [UC-17]")
    void missingCover() throws Exception {
        postJson("/author/comics", """
                {"title":"No Cover","language":"en"}
                """, fixedToken(AUTHOR_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-006 [UC-17]")
    void minAgeTooHigh() throws Exception {
        postJson("/author/comics", """
                {"title":"Adult","language":"en","cover":"https://cdn.example.com/c.png","minimumAge":30}
                """, fixedToken(AUTHOR_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-007 [UC-17]")
    void listOwn() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        createAuthorComic(authorToken, "Listed Draft");
        getJson("/author/comics", authorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-008 [UC-17]")
    void getOwn() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Owned Draft");
        getJson("/author/comics/" + id, authorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-009 [UC-17]")
    void getForeign() throws Exception {
        UUID id = createAuthorComic(fixedToken(AUTHOR_USER), "Foreign Draft");
        getJson("/author/comics/" + id, fixedToken(TRANS_USER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-010 [UC-17]")
    void updateOwn() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Edit Me");
        putJson("/author/comics/" + id, """
                {"title":"Edited Draft","language":"en","cover":"https://cdn.example.com/c.png","summary":"Edited"}
                """, authorToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-011 [UC-17]")
    void updateForbidden() throws Exception {
        UUID id = createAuthorComic(fixedToken(AUTHOR_USER), "Reader Edit");
        putJson("/author/comics/" + id, """
                {"title":"Hacked","language":"en","cover":"https://cdn.example.com/c.png"}
                """, fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-012 [UC-17]")
    void deleteOwn() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Delete Me");
        deleteJson("/author/comics/" + id, authorToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-013 [UC-19]")
    void submitWithoutChapters() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Empty Review");
        postJson("/author/comics/" + id + "/submit-review", "{}", authorToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-014 [UC-19]")
    void submitWithChapter() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        String admin = fixedToken(ADMIN_USER);
        UUID comicId = createAuthorComic(authorToken, "Ready Review");
        createChapterAsAdmin(admin, comicId, "1");
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-015 [UC-19]")
    void submitTwice() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        String admin = fixedToken(ADMIN_USER);
        UUID comicId = createAuthorComic(authorToken, "Double Review");
        createChapterAsAdmin(admin, comicId, "1");
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken).andExpect(status().isOk());
        postJson("/author/comics/" + comicId + "/submit-review", "{}", authorToken).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-016 [UC-54]")
    void appealTooShort() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Short Appeal");
        postJson("/author/comics/" + id + "/appeal", """
                {"reason":"no"}
                """, authorToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-017 [UC-54]")
    void appealValid() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Valid Appeal");
        postJson("/author/comics/" + id + "/appeal", """
                {"reason":"The moderation notes were applied and the pages were redrawn."}
                """, authorToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-AuthorComicController-018 [UC-17]")
    void confirmEdit() throws Exception {
        String authorToken = fixedToken(AUTHOR_USER);
        UUID id = createAuthorComic(authorToken, "Confirm Edit");
        putJson("/author/comics/" + id + "/confirm-edit", "{}", authorToken)
                .andExpect(status().isOk());
    }
}
