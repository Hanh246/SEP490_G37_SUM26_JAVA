package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorUploadTaskResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorUploadAsyncService;
import com.sep.comiverse.service.AuthorUploadTaskService;
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
import com.sep.comiverse.util.BytesMultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/author/comics/{comicId}/chapters")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Chapters", description = "APIs for author chapter ZIP upload, preview, and review submission")
public class AuthorChapterController {

    private final AuthorChapterService authorChapterService;
    private final AuthorUploadTaskService authorUploadTaskService;
    private final AuthorUploadAsyncService authorUploadAsyncService;

    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload chapter ZIP asynchronously", description = "Receives one chapter ZIP file, returns an upload task immediately after the file is accepted, then extracts images and uploads pages to Cloudinary in the background.")
    public ResponseEntity<BaseResponse<AuthorUploadTaskResponse>> uploadChapterZip(
            @PathVariable UUID comicId,
            @Valid @ModelAttribute ChapterUploadRequest request,
            @RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
            @RequestParam(value = "file", required = false) MultipartFile fallbackFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        MultipartFile resolvedZipFile = zipFile != null ? zipFile : fallbackFile;
        BytesMultipartFile safeZipFile = BytesMultipartFile.from(resolvedZipFile, "zipFile");
        AuthorUploadTaskResponse task = authorUploadTaskService.createTask(
                request.getAuthorId(),
                "CHAPTER_ZIP",
                "Chapter ZIP accepted. Backend is processing it in the background."
        );
        authorUploadAsyncService.processChapterZip(task.getTaskId(), comicId, request, safeZipFile);
        return ResponseEntity.accepted().body(BaseResponse.<AuthorUploadTaskResponse>builder()
                .success(true)
                .message("Chapter ZIP accepted. Track status with the returned taskId.")
                .data(task)
                .build());
    }

    @GetMapping("/upload-zip/status/{taskId}")
    @Operation(summary = "Get chapter ZIP upload status", description = "Returns current background processing status for a chapter ZIP upload task")
    public ResponseEntity<BaseResponse<AuthorUploadTaskResponse>> getChapterUploadStatus(
            @PathVariable UUID comicId,
            @PathVariable UUID taskId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorUploadTaskResponse>builder()
                .success(true)
                .data(authorUploadTaskService.getTask(taskId, resolvedAuthorId))
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
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        Page<ChapterPreviewResponse> data = authorChapterService.listChapters(comicId, resolvedAuthorId, safePagination);
        return ResponseEntity.ok(PaginationResponse.<List<ChapterPreviewResponse>>builder()
                .success(true)
                .data(data.getContent())
                .metadata(new PaginationMetadata(
                        safePagination.getPage(),
                        safePagination.getSize(),
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


    @PutMapping("/{chapterId}")
    @Operation(summary = "Update own chapter metadata", description = "Updates editable metadata for one chapter owned by the authenticated author")
    public ResponseEntity<BaseResponse<ChapterPreviewResponse>> updateChapter(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestBody ChapterUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(request == null ? null : request.getAuthorId(), principal);
        return ResponseEntity.ok(BaseResponse.<ChapterPreviewResponse>builder()
                .success(true)
                .message("Chapter updated")
                .data(authorChapterService.updateChapter(comicId, chapterId, resolvedAuthorId, request))
                .build());
    }

    @DeleteMapping("/{chapterId}")
    @Operation(summary = "Permanently delete own chapter", description = "Hard-deletes the owned chapter and removes its dependent task, reading-history, and submission records")
    public ResponseEntity<BaseResponse<Void>> deleteChapter(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        authorChapterService.deleteChapter(comicId, chapterId, resolvedAuthorId);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Chapter permanently deleted")
                .build());
    }
    @PutMapping(value = "/{chapterId}/replace-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace chapter ZIP", description = "Replaces all image URLs of an owned chapter. The new ZIP filename must match the existing chapter number.")
    public ResponseEntity<BaseResponse<ChapterPreviewResponse>> replaceChapterZip(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
            @RequestParam(value = "file", required = false) MultipartFile fallbackFile,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        MultipartFile resolvedZipFile = zipFile != null ? zipFile : fallbackFile;
        return ResponseEntity.ok(BaseResponse.<ChapterPreviewResponse>builder()
                .success(true)
                .message("Chapter ZIP replaced. Submit it for review when the preview is correct.")
                .data(authorChapterService.replaceChapterZip(comicId, chapterId, resolvedAuthorId, resolvedZipFile))
                .build());
    }

    private UUID resolveAuthorId(UUID requestAuthorId, UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        return requestAuthorId;
    }

    private void applyPrincipalAuthorId(ChapterUploadRequest request, UserPrincipal principal) {
        if (request != null && principal != null) {
            request.setAuthorId(principal.getId());
        }
    }
}
