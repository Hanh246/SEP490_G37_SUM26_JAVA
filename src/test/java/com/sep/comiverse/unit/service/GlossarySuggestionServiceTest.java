package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.GlossarySuggestionService;
import com.sep.comiverse.service.OcrService;

import com.sep.comiverse.dto.response.GlossaryTermSuggestionDTO;
import com.sep.comiverse.entity.GlossaryTermEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlossarySuggestionServiceTest {

    @Mock
    private com.sep.comiverse.repository.IGlossaryTermRepository glossaryTermRepository;

    @Mock
    private com.sep.comiverse.repository.IPageTranslationRepository pageTranslationRepository;

    @Mock
    private OcrService ocrService;

    @InjectMocks
    private GlossarySuggestionService glossarySuggestionService;

    private UUID termId1;
    private UUID termId2;
    private UUID termId3;

    @BeforeEach
    void setUp() {
        termId1 = UUID.randomUUID();
        termId2 = UUID.randomUUID();
        termId3 = UUID.randomUUID();
    }

    @Test
    void matchTerms_returnsMatchingGlossaryEntries() {
        List<GlossaryTermEntity> terms = List.of(
                buildTerm(termId1, "Naruto", "Naruto"),
                buildTerm(termId2, "Rasengan", "Luân Xoa"),
                buildTerm(termId3, "Hidden Leaf", "Làng Lá")
        );

        List<GlossaryTermSuggestionDTO> suggestions = matchTerms(
                "Naruto used Rasengan in Hidden Leaf village!",
                terms
        );

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.get(0).getSource()).isEqualTo("Hidden Leaf");
        assertThat(suggestions.get(1).getSource()).isEqualTo("Rasengan");
        assertThat(suggestions.get(2).getSource()).isEqualTo("Naruto");
    }

    @Test
    void matchTerms_isCaseInsensitive() {
        List<GlossaryTermEntity> terms = List.of(buildTerm(termId1, "Sasuke", "Sasuke"));

        List<GlossaryTermSuggestionDTO> suggestions = matchTerms(
                "where is SASUKE going?",
                terms
        );

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getMatchedText()).isEqualTo("Sasuke");
    }

    @Test
    void matchTerms_returnsEmptyListWhenNoMatches() {
        List<GlossaryTermEntity> terms = List.of(buildTerm(termId1, "Kakashi", "Kakashi"));

        List<GlossaryTermSuggestionDTO> suggestions = matchTerms(
                "No glossary words here.",
                terms
        );

        assertThat(suggestions).isEmpty();
    }

    @Test
    void matchTerms_returnsEmptyListForBlankText() {
        List<GlossaryTermEntity> terms = List.of(buildTerm(termId1, "Kakashi", "Kakashi"));

        assertThat(matchTerms("", terms)).isEmpty();
        assertThat(matchTerms("   ", terms)).isEmpty();
        assertThat(matchTerms(null, terms)).isEmpty();
    }

    private List<GlossaryTermSuggestionDTO> matchTerms(
            String text,
            List<GlossaryTermEntity> terms
    ) {
        return ReflectionTestUtils.invokeMethod(
                glossarySuggestionService,
                "matchTerms",
                text,
                terms
        );
    }

    private GlossaryTermEntity buildTerm(UUID id, String source, String target) {
        GlossaryTermEntity term = GlossaryTermEntity.builder()
                .comicId(UUID.randomUUID())
                .source(source)
                .target(target)
                .note("note")
                .build();
        term.setId(id);
        return term;
    }
}
