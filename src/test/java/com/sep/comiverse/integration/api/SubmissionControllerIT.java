package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionControllerIT extends AbstractBlackboxIT {

    @Test
    @DisplayName("TC-INT-SubmissionController-001")
    void listUnauthorized() throws Exception {
        getJson("/submissions").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-002")
    void listAsAdmin() throws Exception {
        getJson("/submissions", fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-003")
    void listAllAsAdmin() throws Exception {
        getJson("/submissions/all", fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-004")
    void createAsAdmin() throws Exception {
        pendingSubmission();
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-005")
    void getById() throws Exception {
        UUID id = pendingSubmission();
        getJson("/submissions/" + id, fixedToken(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-006")
    void getUnknown() throws Exception {
        getJson("/submissions/" + UUID.randomUUID(), fixedToken(ADMIN_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-007")
    void updateAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id, """
                {"title":"Updated submission","status":"pending","queueType":"author"}
                """, fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-008")
    void deleteAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        deleteJson("/submissions/" + id, fixedToken(ADMIN_USER)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-009")
    void claimForbidden() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/claim", "{}", fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-010")
    void claimAsModerator() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/claim", "{}", fixedToken(MOD_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-011")
    void releaseAsModerator() throws Exception {
        UUID id = pendingSubmission();
        String mod = fixedToken(MOD_USER);
        putJson("/submissions/" + id + "/claim", "{}", mod).andExpect(status().isOk());
        putJson("/submissions/" + id + "/release", "{}", mod).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-012")
    void approveForbidden() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/approve", "{}", fixedToken(READER_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-013")
    void approveAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/approve", "{}", fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-014")
    void rejectAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/reject", """
                {"reason":"Needs clearer lettering"}
                """, fixedToken(ADMIN_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-015")
    void claimUnknown() throws Exception {
        putJson("/submissions/" + UUID.randomUUID() + "/claim", "{}", fixedToken(MOD_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-016")
    void invalidPageSize() throws Exception {
        getJson("/submissions?size=0", fixedToken(ADMIN_USER)).andExpect(status().isBadRequest());
    }

    private UUID pendingSubmission() throws Exception {
        SeededUser author = fixedUser(AUTHOR_USER);
        String admin = fixedToken(ADMIN_USER);
        UUID comicId = createComicAsAdmin(admin, author.id(), "Submission Host");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        return createSubmission(admin, comicId, chapterId, author.id());
    }
}
