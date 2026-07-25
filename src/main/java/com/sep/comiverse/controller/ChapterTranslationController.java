package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterTranslationDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Read-only, public-facing: lets the reader fetch every available
// translation (language + its bubbles JSON) for a chapter in a single
// call, so it can offer a language switcher without extra round-trips.
// Also exposes a comic-level "which languages exist at all" endpoint, used
// by ComicDetail's language picker (shown before entering any chapter).
@RestController
@RequiredArgsConstructor
public class ChapterTranslationController {

    private final IChapterTranslationRepository chapterTranslationRepository;

    @GetMapping("/chapters/{chapterId}/translations")
    public ResponseEntity<BaseResponse<List<ChapterTranslationDTO>>> getTranslations(@PathVariable UUID chapterId) {
        List<ChapterTranslationEntity> translations = chapterTranslationRepository.findByChapter_Id(chapterId);

        List<ChapterTranslationDTO> result = translations.stream()
                .map(t -> ChapterTranslationDTO.builder()
                        .id(t.getId())
                        .languageCode(t.getLanguageCode())
                        .pagesBubbles(t.getPagesBubbles())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(BaseResponse.<List<ChapterTranslationDTO>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping("/comics/{comicId}/translation-languages")
    public ResponseEntity<BaseResponse<List<String>>> getAvailableLanguages(@PathVariable UUID comicId) {
        List<String> languages = chapterTranslationRepository.findDistinctLanguageCodesByComicId(comicId);
        return ResponseEntity.ok(BaseResponse.<List<String>>builder()
                .success(true)
                .data(languages)
                .build());
    }
}