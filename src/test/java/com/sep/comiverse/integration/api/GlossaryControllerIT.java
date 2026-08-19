package com.sep.comiverse.integration.api;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import com.sep.comiverse.service.OcrService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlossaryControllerIT extends AbstractBlackboxIT {

    @MockBean
    private OcrService ocrService;

    @Test
    @DisplayName("TC-INT-GlossaryController-001 [UC-47]: GET /glossary/comic/{comicId} - missing token should be rejected")
    void listUnauthorized() throws Exception {
        getJson("/glossary/comic/" + UUID.randomUUID()).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-002 [UC-47]: GET /glossary/comic/{comicId} - AUTHOR should return 200")
    void listAsAuthor() throws Exception {
        getJson("/glossary/comic/" + UUID.randomUUID(), token("AUTHOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-003 [UC-47]: POST /glossary/comic/{comicId} - AUTHOR should return 201")
    void createAsAuthor() throws Exception {
        createTerm(token("AUTHOR"), UUID.randomUUID(), "senpai", "đàn anh");
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-004 [UC-47]: POST /glossary/comic/{comicId} - missing source should return 400")
    void missingSource() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID(), """
                {"target":"đàn anh"}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-005 [UC-47]: POST /glossary/comic/{comicId} - missing target should return 400")
    void missingTarget() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID(), """
                {"source":"senpai"}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-006 [UC-47]: POST /glossary/comic/{comicId} - blank source should return 400")
    void blankSource() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID(), """
                {"source":"   ","target":"đàn anh"}
                """, token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-007 [UC-47]: GET /glossary/comic/{comicId} - created term should be listed")
    void listAfterCreate() throws Exception {
        String token = token("AUTHOR");
        UUID comicId = UUID.randomUUID();
        createTerm(token, comicId, "senpai", "đàn anh");
        getJson("/glossary/comic/" + comicId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].source").value("senpai"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-008 [UC-47]: PUT /glossary/{id} - owner should return 200")
    void updateTerm() throws Exception {
        String token = token("AUTHOR");
        UUID termId = createTerm(token, UUID.randomUUID(), "senpai", "đàn anh");
        putJson("/glossary/" + termId, """
                {"source":"senpai","target":"tiền bối","note":"honorific"}
                """, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("tiền bối"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-009 [UC-47]: PUT /glossary/{id} - unknown id should return 404")
    void updateUnknown() throws Exception {
        putJson("/glossary/" + UUID.randomUUID(), """
                {"target":"x"}
                """, token("AUTHOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-010 [UC-47]: DELETE /glossary/{id} - AUTHOR should return 200")
    void deleteTerm() throws Exception {
        String token = token("AUTHOR");
        UUID termId = createTerm(token, UUID.randomUUID(), "delete-me", "xóa");
        deleteJson("/glossary/" + termId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-011 [UC-47]: DELETE /glossary/{id} - unknown id should return 404")
    void deleteUnknown() throws Exception {
        deleteJson("/glossary/" + UUID.randomUUID(), token("AUTHOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-012 [UC-16]: POST /glossary/comic/{comicId}/suggest - missing pageId and imageUrl should return 400")
    void suggestMissingInput() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID() + "/suggest", "{}", token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-013 [UC-16]: POST /glossary/comic/{comicId}/suggest - unknown pageId should return 400")
    void suggestUnknownPage() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID() + "/suggest", """
                {"pageId":"%s"}
                """.formatted(UUID.randomUUID()), token("AUTHOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-014 [UC-16]: POST /glossary/comic/{comicId}/suggest - imageUrl should return OCR text")
    void suggestWithImageUrl() throws Exception {
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("Hello senpai");
        postJson("/glossary/comic/" + UUID.randomUUID() + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, token("AUTHOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedText").value("Hello senpai"))
                .andExpect(jsonPath("$.suggestions", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-015 [UC-16]: POST /glossary/comic/{comicId}/suggest - existing term should be matched")
    void suggestMatchesTerm() throws Exception {
        String token = token("AUTHOR");
        UUID comicId = UUID.randomUUID();
        createTerm(token, comicId, "senpai", "đàn anh");
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("Thank you senpai");
        postJson("/glossary/comic/" + comicId + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].source").value("senpai"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-016 [UC-47]: GET /glossary/project/{comicId} - project alias should return 200")
    void listProjectAlias() throws Exception {
        getJson("/glossary/project/" + UUID.randomUUID(), token("TRANSLATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-017 [UC-47]: POST /glossary/project/{comicId} - project alias should return 201")
    void createProjectAlias() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID(), """
                {"source":"kawaii","target":"dễ thương"}
                """, token("TRANSLATOR"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-018 [UC-16]: POST /glossary/comic/{comicId}/suggest - TRANSLATOR should return 200")
    void suggestAsTranslator() throws Exception {
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("empty page");
        postJson("/glossary/comic/" + UUID.randomUUID() + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, token("TRANSLATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedText").value("empty page"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-019 [UC-16]: POST /glossary/comic/{comicId}/suggest - READER should return 200")
    void suggestAsReader() throws Exception {
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("reader page");
        postJson("/glossary/comic/" + UUID.randomUUID() + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, token("READER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-020 [UC-47]: PUT /glossary/{id} - note-only update should return 200")
    void updateNote() throws Exception {
        String token = token("AUTHOR");
        UUID termId = createTerm(token, UUID.randomUUID(), "sensei", "thầy");
        putJson("/glossary/" + termId, """
                {"note":"teacher honorific"}
                """, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("teacher honorific"));
    }

    private UUID createTerm(String token, UUID comicId, String source, String target) throws Exception {
        MvcResult result = postJson("/glossary/comic/" + comicId, """
                {"source":"%s","target":"%s","note":"it"}
                """.formatted(source, target), token)
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readTree(result).path("id").asText());
    }
}
