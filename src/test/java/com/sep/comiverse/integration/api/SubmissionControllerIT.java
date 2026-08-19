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
    @DisplayName("TC-INT-SubmissionController-001 [UC-19]: GET /submissions - missing token should be rejected")
    void listUnauthorized() throws Exception {
        getJson("/submissions").andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-002 [UC-19]: GET /submissions - ADMIN should return 200")
    void listAsAdmin() throws Exception {
        getJson("/submissions", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-003 [UC-19]: GET /submissions/all - ADMIN should return 200")
    void listAllAsAdmin() throws Exception {
        getJson("/submissions/all", token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-004 [UC-19]: POST /submissions - ADMIN should return 201")
    void createAsAdmin() throws Exception {
        pendingSubmission();
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-005 [UC-19]: GET /submissions/{id} - ADMIN should return 200")
    void getById() throws Exception {
        UUID id = pendingSubmission();
        getJson("/submissions/" + id, token("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-006 [UC-19]: GET /submissions/{id} - unknown id should return 404")
    void getUnknown() throws Exception {
        getJson("/submissions/" + UUID.randomUUID(), token("ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-007 [UC-19]: PUT /submissions/{id} - ADMIN should return 200")
    void updateAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id, """
                {"title":"Updated submission","status":"pending","queueType":"author"}
                """, token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-008 [UC-19]: DELETE /submissions/{id} - ADMIN should return 200")
    void deleteAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        deleteJson("/submissions/" + id, token("ADMIN")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-009 [UC-19]: PUT /submissions/{id}/claim - READER should return 403")
    void claimForbidden() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/claim", "{}", token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-010 [UC-19]: PUT /submissions/{id}/claim - MODERATOR should return 200")
    void claimAsModerator() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/claim", "{}", token("MODERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-011 [UC-19]: PUT /submissions/{id}/release - MODERATOR should return 200")
    void releaseAsModerator() throws Exception {
        UUID id = pendingSubmission();
        String mod = token("MODERATOR");
        putJson("/submissions/" + id + "/claim", "{}", mod).andExpect(status().isOk());
        putJson("/submissions/" + id + "/release", "{}", mod).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-012 [UC-19]: PUT /submissions/{id}/approve - READER should return 403")
    void approveForbidden() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/approve", "{}", token("READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-013 [UC-19]: PUT /submissions/{id}/approve - ADMIN should return 200")
    void approveAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/approve", "{}", token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-014 [UC-19]: PUT /submissions/{id}/reject - ADMIN should return 200")
    void rejectAsAdmin() throws Exception {
        UUID id = pendingSubmission();
        putJson("/submissions/" + id + "/reject", """
                {"reason":"Needs clearer lettering"}
                """, token("ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-015 [UC-19]: PUT /submissions/{id}/claim - unknown id should return 404")
    void claimUnknown() throws Exception {
        putJson("/submissions/" + UUID.randomUUID() + "/claim", "{}", token("MODERATOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-SubmissionController-016 [UC-19]: GET /submissions - invalid page size should return 400")
    void invalidPageSize() throws Exception {
        getJson("/submissions?size=0", token("ADMIN")).andExpect(status().isBadRequest());
    }

    private UUID pendingSubmission() throws Exception {
        SeededUser author = seedUser("AUTHOR");
        String admin = token("ADMIN");
        UUID comicId = createComicAsAdmin(admin, author.id(), "Submission Host");
        UUID chapterId = createChapterAsAdmin(admin, comicId, "1");
        return createSubmission(admin, comicId, chapterId, author.id());
    }
}
