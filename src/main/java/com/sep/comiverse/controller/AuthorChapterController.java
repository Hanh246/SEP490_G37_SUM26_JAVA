package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/author/comics/{comicId}/chapters")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Chapters", description = "APIs for author chapter ZIP upload, preview, and review submission")
public class AuthorChapterController {

    private final AuthorChapterService authorChapterService;

    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload chapter ZIP", description = "Receives one ZIP file, extracts images, sorts pages by filename, uploads pages to Cloudinary, and returns a preview")
    public ResponseEntity<BaseResponse<ChapterPreviewResponse>> uploadChapterZip(
            @PathVariable UUID comicId,
            @Valid @ModelAttribute ChapterUploadRequest request,
            @RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
            @RequestParam(value = "file", required = false) MultipartFile fallbackFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        MultipartFile resolvedZipFile = zipFile != null ? zipFile : fallbackFile;
        return ResponseEntity.ok(BaseResponse.<ChapterPreviewResponse>builder()
                .success(true)
                .message("Chapter ZIP uploaded and preview is ready")
                .data(authorChapterService.uploadChapterZip(comicId, request, resolvedZipFile))
                .build());
    }

    @GetMapping
    @Operation(summary = "List chapter previews", description = "Returns chapters for one comic owned by the authenticated author")
    public ResponseEntity<PaginationResponse<List<ChapterPreviewResponse>>> listChapters(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @Valid @ParameterObject PaginationSearchDTO pagination,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        Page<ChapterPreviewResponse> data = authorChapterService.listChapters(comicId, resolvedAuthorId, pagination);
        return ResponseEntity.ok(PaginationResponse.<List<ChapterPreviewResponse>>builder()
                .success(true)
                .data(data.getContent())
                .metadata(new PaginationMetadata(
                        pagination.getPage(),
                        pagination.getSize(),
                        data.getTotalElements(),
                        data.getTotalPages()
                ))
                .build());
    }

    @GetMapping("/{chapterId}/preview")
    @Operation(summary = "Preview chapter", description = "Returns sorted chapter page URLs before author submits the chapter to moderation")
    public ResponseEntity<BaseResponse<ChapterPreviewResponse>> previewChapter(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<ChapterPreviewResponse>builder()
                .success(true)
                .data(authorChapterService.previewChapter(comicId, chapterId, resolvedAuthorId))
                .build());
    }

    @PostMapping("/{chapterId}/submit-review")
    @Operation(summary = "Submit chapter for review", description = "Moves a preview-ready chapter to the moderator review queue")
    public ResponseEntity<BaseResponse<SubmitChapterReviewResponse>> submitForReview(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<SubmitChapterReviewResponse>builder()
                .success(true)
                .data(authorChapterService.submitForReview(comicId, chapterId, resolvedAuthorId))
                .build());
    }

    private UUID resolveAuthorId(UUID requestAuthorId, UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        return requestAuthorId;
    }

    private void applyPrincipalAuthorId(ChapterUploadRequest request, UserPrincipal principal) {
        if (principal != null) {
            request.setAuthorId(principal.getId());
        }
    }
}
