package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.GlossarySuggestRequest;
import com.sep.comiverse.dto.response.GlossarySuggestionResponse;
import com.sep.comiverse.dto.response.GlossaryTermSuggestionDTO;
import com.sep.comiverse.entity.GlossaryTermEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.repository.IGlossaryTermRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GlossarySuggestionService {

    private final IGlossaryTermRepository glossaryTermRepository;
    private final IPageTranslationRepository pageTranslationRepository;
    private final OcrService ocrService;

    public GlossarySuggestionResponse suggestFromPage(UUID comicId, GlossarySuggestRequest request) {
        String imageUrl = resolveImageUrl(request);
        String extractedText = ocrService.extractTextFromImageUrl(imageUrl);
        List<GlossaryTermEntity> terms = glossaryTermRepository.findByComicIdOrderByCreatedAtDesc(comicId);
        List<GlossaryTermSuggestionDTO> suggestions = matchTerms(extractedText, terms);

        return GlossarySuggestionResponse.builder()
                .extractedText(extractedText)
                .suggestions(suggestions)
                .build();
    }

    public GlossarySuggestionResponse suggestFromText(UUID comicId, String text) {
        List<GlossaryTermEntity> terms = glossaryTermRepository.findByComicIdOrderByCreatedAtDesc(comicId);
        List<GlossaryTermSuggestionDTO> suggestions = matchTerms(text, terms);

        return GlossarySuggestionResponse.builder()
                .extractedText(text != null ? text.trim() : "")
                .suggestions(suggestions)
                .build();
    }

    List<GlossaryTermSuggestionDTO> matchTerms(String pageText, List<GlossaryTermEntity> terms) {
        if (pageText == null || pageText.isBlank() || terms == null || terms.isEmpty()) {
            return List.of();
        }

        String normalizedPageText = normalize(pageText);
        List<GlossaryTermSuggestionDTO> suggestions = new ArrayList<>();

        for (GlossaryTermEntity term : terms) {
            if (term.getSource() == null || term.getSource().isBlank()) {
                continue;
            }

            String normalizedSource = normalize(term.getSource());
            if (normalizedSource.isEmpty() || !normalizedPageText.contains(normalizedSource)) {
                continue;
            }

            suggestions.add(GlossaryTermSuggestionDTO.builder()
                    .id(term.getId())
                    .source(term.getSource())
                    .target(term.getTarget())
                    .note(term.getNote())
                    .matchedText(term.getSource())
                    .build());
        }

        suggestions.sort(Comparator.comparingInt((GlossaryTermSuggestionDTO item) -> item.getSource().length()).reversed());
        return suggestions;
    }

    private String resolveImageUrl(GlossarySuggestRequest request) {
        if (request.getPageId() != null) {
            PageTranslationEntity page = pageTranslationRepository.findById(request.getPageId())
                    .orElseThrow(() -> new IllegalArgumentException("Page not found"));
            if (page.getImageUrl() == null || page.getImageUrl().isBlank()) {
                throw new IllegalArgumentException("Page does not have an image URL");
            }
            return page.getImageUrl();
        }

        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            return request.getImageUrl().trim();
        }

        throw new IllegalArgumentException("Either pageId or imageUrl is required");
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
