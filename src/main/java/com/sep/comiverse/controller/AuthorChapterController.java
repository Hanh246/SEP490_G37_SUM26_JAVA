package com.sep.comiverse.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorUploadTaskResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorUploadAsyncService;
import com.sep.comiverse.service.AuthorUploadTaskService;
import com.sep.comiverse.util.BytesMultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Author - Chapters", description = "APIs for author chapter folder upload, preview, and review submission")
public class AuthorChapterController {

    private final AuthorChapterService authorChapterService;
    private final AuthorUploadTaskService authorUploadTaskService;
    private final AuthorUploadAsyncService authorUploadAsyncService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/upload-folder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload chapter folder asynchronously",
            description = "Receives page images directly as multipart files. No ZIP or CBZ archive is accepted."
    )
    public ResponseEntity<BaseResponse<AuthorUploadTaskResponse>> uploadChapterFolder(
            @PathVariable UUID comicId,
            @Valid @ModelAttribute ChapterUploadRequest request,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("relativePathsJson") String relativePathsJson,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        List<String> relativePaths = parseRelativePaths(relativePathsJson);
        List<MultipartFile> safeFiles = copyFilesForAsync(files);

        AuthorUploadTaskResponse task = authorUploadTaskService.createTask(
                request.getAuthorId(),
                "CHAPTER_FOLDER",
                "Chapter folder accepted. Backend is processing the page images in the background."
        );

        authorUploadAsyncService.processChapterFolder(
                task.getTaskId(),
                comicId,
                request,
                safeFiles,
                relativePaths
        );

        return ResponseEntity.accepted().body(BaseResponse.<AuthorUploadTaskResponse>builder()
                .success(true)
                .message("Chapter folder accepted. Track status with the returned taskId.")
                .data(task)
                .build());
    }

    @GetMapping("/upload-folder/status/{taskId}")
    @Operation(summary = "Get chapter folder upload status")
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

    @PutMapping(value = "/{chapterId}/replace-folder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Replace chapter folder",
            description = "Replaces all chapter pages with directly uploaded folder images. No ZIP or CBZ archive is accepted."
    )
    public ResponseEntity<BaseResponse<ChapterPreviewResponse>> replaceChapterFolder(
            @PathVariable UUID comicId,
            @PathVariable UUID chapterId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("relativePathsJson") String relativePathsJson,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        List<String> relativePaths = parseRelativePaths(relativePathsJson);

        return ResponseEntity.ok(BaseResponse.<ChapterPreviewResponse>builder()
                .success(true)
                .message("Chapter folder replaced. Submit it for review when the preview is correct.")
                .data(authorChapterService.replaceChapterFolder(
                        comicId,
                        chapterId,
                        resolvedAuthorId,
                        files,
                        relativePaths
                ))
                .build());
    }

    private List<String> parseRelativePaths(String relativePathsJson) {
        if (relativePathsJson == null || relativePathsJson.isBlank()) {
            throw new CustomException(400, "relativePathsJson is required", HttpStatus.BAD_REQUEST);
        }
        try {
            List<String> paths = objectMapper.readValue(relativePathsJson, new TypeReference<List<String>>() {
            });
            if (paths == null || paths.isEmpty()) {
                throw new CustomException(400, "relativePathsJson must contain at least one path", HttpStatus.BAD_REQUEST);
            }
            return paths;
        } catch (JsonProcessingException error) {
            throw new CustomException(400, "relativePathsJson must be a valid JSON string array", HttpStatus.BAD_REQUEST);
        }
    }

    private List<MultipartFile> copyFilesForAsync(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(400, "Chapter folder must contain at least one image", HttpStatus.BAD_REQUEST);
        }
        return files.stream()
                .map(file -> (MultipartFile) BytesMultipartFile.from(file, "files"))
                .toList();
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
