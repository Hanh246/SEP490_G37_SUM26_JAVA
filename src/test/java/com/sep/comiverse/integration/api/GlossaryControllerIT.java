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
    @DisplayName("TC-INT-GlossaryController-001 [UC-47]")
    void listUnauthorized() throws Exception {
        getJson("/glossary/project/" + UUID.randomUUID()).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-002 [UC-47]")
    void listAsLeader() throws Exception {
        getJson("/glossary/project/" + UUID.randomUUID(), fixedToken(LEADER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-003 [UC-47]")
    void createAsTranslator() throws Exception {
        createTerm(fixedToken(TRANS_USER), UUID.randomUUID(), "senpai", "đàn anh");
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-004 [UC-47]")
    void missingSource() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID(), """
                {"target":"đàn anh"}
                """, fixedToken(TRANS_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-005 [UC-47]")
    void missingTarget() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID(), """
                {"source":"senpai"}
                """, fixedToken(TRANS_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-006 [UC-47]")
    void blankSource() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID(), """
                {"source":"   ","target":"đàn anh"}
                """, fixedToken(TRANS_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-007 [UC-47]")
    void listAfterCreate() throws Exception {
        String translator = fixedToken(TRANS_USER);
        UUID comicId = UUID.randomUUID();
        createTerm(translator, comicId, "senpai", "đàn anh");
        getJson("/glossary/project/" + comicId, fixedToken(LEADER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].source").value("senpai"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-008 [UC-47]")
    void updateTerm() throws Exception {
        String translator = fixedToken(TRANS_USER);
        UUID termId = createTerm(translator, UUID.randomUUID(), "senpai", "đàn anh");
        putJson("/glossary/" + termId, """
                {"source":"senpai","target":"tiền bối","note":"honorific"}
                """, translator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("tiền bối"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-009 [UC-47]")
    void updateUnknown() throws Exception {
        putJson("/glossary/" + UUID.randomUUID(), """
                {"target":"x"}
                """, fixedToken(LEADER_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-010 [UC-47]")
    void deleteTerm() throws Exception {
        String translator = fixedToken(TRANS_USER);
        UUID termId = createTerm(translator, UUID.randomUUID(), "delete-me", "xóa");
        deleteJson("/glossary/" + termId, translator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-011 [UC-47]")
    void deleteUnknown() throws Exception {
        deleteJson("/glossary/" + UUID.randomUUID(), fixedToken(LEADER_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-012 [UC-16]")
    void suggestMissingInput() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID() + "/suggest", "{}", fixedToken(TRANS_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-013 [UC-16]")
    void suggestUnknownPage() throws Exception {
        postJson("/glossary/project/" + UUID.randomUUID() + "/suggest", """
                {"pageId":"%s"}
                """.formatted(UUID.randomUUID()), fixedToken(TRANS_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-014 [UC-16]")
    void suggestAsTranslator() throws Exception {
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("Hello senpai");
        postJson("/glossary/project/" + UUID.randomUUID() + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, fixedToken(TRANS_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedText").value("Hello senpai"))
                .andExpect(jsonPath("$.suggestions", notNullValue()));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-015 [UC-16]")
    void suggestMatchesTerm() throws Exception {
        String translator = fixedToken(TRANS_USER);
        UUID comicId = UUID.randomUUID();
        createTerm(translator, comicId, "senpai", "đàn anh");
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("Thank you senpai");
        postJson("/glossary/project/" + comicId + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, translator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].source").value("senpai"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-016 [UC-47]")
    void listComicAlias() throws Exception {
        getJson("/glossary/comic/" + UUID.randomUUID(), fixedToken(TRANS_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-017 [UC-47]")
    void createAsLeader() throws Exception {
        postJson("/glossary/comic/" + UUID.randomUUID(), """
                {"source":"kawaii","target":"dễ thương"}
                """, fixedToken(LEADER_USER))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-018 [UC-16]")
    void suggestAsLeader() throws Exception {
        when(ocrService.extractTextFromImageUrl(anyString())).thenReturn("empty page");
        postJson("/glossary/project/" + UUID.randomUUID() + "/suggest", """
                {"imageUrl":"https://cdn.example.com/page.png"}
                """, fixedToken(LEADER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedText").value("empty page"));
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-019 [UC-47]")
    void listAsTranslator() throws Exception {
        getJson("/glossary/project/" + UUID.randomUUID(), fixedToken(TRANS_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-INT-GlossaryController-020 [UC-47]")
    void updateNoteAsLeader() throws Exception {
        UUID termId = createTerm(fixedToken(TRANS_USER), UUID.randomUUID(), "sensei", "thầy");
        putJson("/glossary/" + termId, """
                {"note":"teacher honorific"}
                """, fixedToken(LEADER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("teacher honorific"));
    }

    private UUID createTerm(String token, UUID comicId, String source, String target) throws Exception {
        MvcResult result = postJson("/glossary/project/" + comicId, """
                {"source":"%s","target":"%s","note":"it"}
                """.formatted(source, target), token)
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readTree(result).path("id").asText());
    }
}
