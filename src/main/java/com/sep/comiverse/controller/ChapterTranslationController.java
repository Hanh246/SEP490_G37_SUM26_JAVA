package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterTranslationDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Chapter Translations", description = "Endpoints to view and fetch chapter translations and languages")
public class ChapterTranslationController {

    private final IChapterTranslationRepository chapterTranslationRepository;
    private final ChapterCrudPlugin chapterCrudPlugin;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/chapters/{chapterId}/translations")
    @Operation(summary = "Get chapter translations", description = "Fetch all available published language translations for a specific chapter")
    public ResponseEntity<BaseResponse<List<ChapterTranslationDTO>>> getTranslations(@PathVariable UUID chapterId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (!chapterCrudPlugin.canAccessChapterContent(chapterId, userId)) {
            return ResponseEntity.ok(BaseResponse.<List<ChapterTranslationDTO>>builder()
                    .success(true)
                    .data(List.of())
                    .build());
        }

        List<ChapterTranslationEntity> translations = chapterTranslationRepository.findPublishedByChapterId(chapterId);

        List<ChapterTranslationDTO> result = translations.stream()
                .map(t -> ChapterTranslationDTO.builder()
                        .id(t.getId())
                        .chapterId(chapterId)
                        .languageCode(t.getLanguageCode())
                        .pagesBubbles(t.getPagesBubbles())
                        .projectTeamId(t.getProjectTeamId())
                        .status(t.getStatus())
                        .createdAt(t.getCreatedAt())
                        .updatedAt(t.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(BaseResponse.<List<ChapterTranslationDTO>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping({"/chapters/translations/{id}"})
    @Operation(summary = "Get translation by ID", description = "Retrieve detailed information of a chapter translation by its ID")
    public ResponseEntity<BaseResponse<ChapterTranslationDTO>> getTranslationById(@PathVariable UUID id) {
        ChapterTranslationEntity translation = chapterTranslationRepository.findByIdWithDetails(id)
                .or(() -> chapterTranslationRepository.findById(id))
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new CustomException(404, "Translation not found", HttpStatus.NOT_FOUND));

        ChapterEntity chapter = translation.getChapter();
        UUID chapterId = chapter != null ? chapter.getId() : null;
        String chapterNumber = chapter != null ? chapter.getChapterNumber() : null;
        UUID comicId = (chapter != null && chapter.getComic() != null) ? chapter.getComic().getId() : null;
        String comicTitle = (chapter != null && chapter.getComic() != null) ? chapter.getComic().getTitle() : null;

        ChapterTranslationDTO dto = ChapterTranslationDTO.builder()
                .id(translation.getId())
                .chapterId(chapterId)
                .chapterNumber(chapterNumber)
                .comicId(comicId)
                .comicTitle(comicTitle)
                .languageCode(translation.getLanguageCode())
                .pagesBubbles(translation.getPagesBubbles())
                .projectTeamId(translation.getProjectTeamId())
                .status(translation.getStatus())
                .createdAt(translation.getCreatedAt())
                .updatedAt(translation.getUpdatedAt())
                .build();

        return ResponseEntity.ok(BaseResponse.<ChapterTranslationDTO>builder()
                .success(true)
                .data(dto)
                .build());
    }

    @PatchMapping("/chapters/translations/{id}/status")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'PROJECT_LEADER', 'ADMIN')")
    @Operation(summary = "Update translation status", description = "Update the status of a translation (PUBLISHED, UNPUBLISHED, DRAFT)")
    public ResponseEntity<BaseResponse<ChapterTranslationDTO>> updateTranslationStatus(
            @PathVariable UUID id,
            @RequestParam("status") ChapterTranslationStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChapterTranslationEntity translation = chapterTranslationRepository.findByIdWithDetails(id)
                .or(() -> chapterTranslationRepository.findById(id))
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new CustomException(404, "Translation not found", HttpStatus.NOT_FOUND));

        if (principal != null && principal.user() != null && principal.user().getRole() != null) {
            String roleName = principal.user().getRole().getRoleName();
            if ("PROJECT_LEADER".equalsIgnoreCase(roleName)) {
                boolean isLeader = chapterTranslationRepository.isUserLeaderOfTranslation(id, principal.getId());
                if (!isLeader) {
                    throw new CustomException(403, "Only the team leader of this translation can modify its status", HttpStatus.FORBIDDEN);
                }
            }
        }

        translation.setStatus(status);
        ChapterTranslationEntity saved = chapterTranslationRepository.save(translation);

        if (saved.getChapter() != null && saved.getChapter().getComic() != null) {
            chapterCrudPlugin.evictChaptersCache(saved.getChapter().getComic().getId());
        }

        ChapterEntity chapter = saved.getChapter();
        UUID chapterId = chapter != null ? chapter.getId() : null;
        String chapterNumber = chapter != null ? chapter.getChapterNumber() : null;
        UUID comicId = (chapter != null && chapter.getComic() != null) ? chapter.getComic().getId() : null;
        String comicTitle = (chapter != null && chapter.getComic() != null) ? chapter.getComic().getTitle() : null;

        ChapterTranslationDTO dto = ChapterTranslationDTO.builder()
                .id(saved.getId())
                .chapterId(chapterId)
                .chapterNumber(chapterNumber)
                .comicId(comicId)
                .comicTitle(comicTitle)
                .languageCode(saved.getLanguageCode())
                .pagesBubbles(saved.getPagesBubbles())
                .projectTeamId(saved.getProjectTeamId())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();

        return ResponseEntity.ok(BaseResponse.<ChapterTranslationDTO>builder()
                .success(true)
                .data(dto)
                .message("Translation status updated successfully")
                .build());
    }

    @GetMapping("/comics/{comicId}/translation-languages")
    @Operation(summary = "Get available translation languages for a comic", description = "Retrieve list of language codes that have at least one translated chapter in the comic")
    public ResponseEntity<BaseResponse<List<String>>> getAvailableLanguages(@PathVariable UUID comicId) {
        List<String> languages = chapterTranslationRepository.findDistinctLanguageCodesByComicId(comicId).stream()
                .filter(code -> code != null && !code.isBlank())
                .map(com.sep.comiverse.util.LanguageCodes::normalize)
                .distinct()
                .toList();
        return ResponseEntity.ok(BaseResponse.<List<String>>builder()
                .success(true)
                .data(languages)
                .build());
    }
}
