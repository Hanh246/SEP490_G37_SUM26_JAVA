package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.AuthorUploadTaskResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorComicService;
import com.sep.comiverse.service.AuthorUploadAsyncService;
import com.sep.comiverse.service.AuthorUploadTaskService;
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
import com.sep.comiverse.util.BytesMultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/author/comics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Comics", description = "APIs for author comic creation and management")
public class AuthorComicController {

    private final AuthorComicService authorComicService;
    private final AuthorUploadTaskService authorUploadTaskService;
    private final AuthorUploadAsyncService authorUploadAsyncService;

    @PostMapping
    @Operation(summary = "Create a new comic", description = "Creates a comic owned by the authenticated author and sends it to moderation review")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> createComic(
            @Valid @RequestBody AuthorComicCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        AuthorComicResponse data = authorComicService.createComic(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<AuthorComicResponse>builder()
                        .success(true)
                        .message("Comic submitted for moderator review")
                        .data(data)
                        .build());
    }


    @PostMapping(value = "/upload-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a full comic package asynchronously", description = "Receives one outer ZIP, returns an upload task immediately after the file is accepted, then processes chapter CBZ extraction and Cloudinary upload in the background.")
    public ResponseEntity<BaseResponse<AuthorUploadTaskResponse>> uploadComicPackage(
            @ModelAttribute AuthorComicCreateRequest request,
            @RequestParam(value = "comicZip", required = false) MultipartFile comicZip,
            @RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
            @RequestParam(value = "file", required = false) MultipartFile fallbackFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        MultipartFile resolvedZipFile = comicZip != null ? comicZip : (zipFile != null ? zipFile : fallbackFile);
        BytesMultipartFile safeZipFile = BytesMultipartFile.from(resolvedZipFile, "comicZip");
        AuthorUploadTaskResponse task = authorUploadTaskService.createTask(
                request.getAuthorId(),
                "COMIC_PACKAGE",
                "Comic package accepted. Backend is processing it in the background."
        );
        authorUploadAsyncService.processComicPackage(task.getTaskId(), request, safeZipFile);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.<AuthorUploadTaskResponse>builder()
                        .success(true)
                        .message("Comic package accepted. Track status with the returned taskId.")
                        .data(task)
                        .build());
    }

    @GetMapping("/upload-package/status/{taskId}")
    @Operation(summary = "Get comic package upload status", description = "Returns current background processing status for a comic package upload task")
    public ResponseEntity<BaseResponse<AuthorUploadTaskResponse>> getComicPackageUploadStatus(
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
    @Operation(summary = "List own comics", description = "Returns comics owned by the authenticated author")
    public ResponseEntity<PaginationResponse<List<AuthorComicResponse>>> listOwnComics(
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @Valid @ParameterObject PaginationSearchDTO pagination,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        Page<AuthorComicResponse> data = authorComicService.listOwnComics(resolvedAuthorId, safePagination);
        return ResponseEntity.ok(
                PaginationResponse.<List<AuthorComicResponse>>builder()
                        .success(true)
                        .data(data.getContent())
                        .metadata(new PaginationMetadata(
                                safePagination.getPage(),
                                safePagination.getSize(),
                                data.getTotalElements(),
                                data.getTotalPages()
                        ))
                        .build()
        );
    }

    @GetMapping("/{comicId}")
    @Operation(summary = "Get own comic detail", description = "Returns detail for one comic owned by the authenticated author")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> getComic(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorComicResponse>builder()
                .success(true)
                .data(authorComicService.getComic(comicId, resolvedAuthorId))
                .build());
    }

    @PutMapping("/{comicId}")
    @Operation(summary = "Update own comic", description = "Updates editable comic information for the authenticated author")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> updateComic(
            @PathVariable UUID comicId,
            @Valid @RequestBody AuthorComicUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorComicResponse>builder()
                .success(true)
                .message("Comic information updated")
                .data(authorComicService.updateComic(comicId, request))
                .build());
    }


    @DeleteMapping("/{comicId}")
    @Operation(summary = "Soft-delete own comic", description = "Marks an owned comic as deleted so it is removed from author/public listings")
    public ResponseEntity<BaseResponse<Void>> deleteComic(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        authorComicService.deleteComic(comicId, resolvedAuthorId);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Comic deleted")
                .build());
    }

    private UUID resolveAuthorId(UUID requestAuthorId, UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        return requestAuthorId;
    }

    private void applyPrincipalAuthorId(AuthorComicCreateRequest request, UserPrincipal principal) {
        if (request != null && principal != null) {
            request.setAuthorId(principal.getId());
        }
    }

    private void applyPrincipalAuthorId(AuthorComicUpdateRequest request, UserPrincipal principal) {
        if (request != null && principal != null) {
            request.setAuthorId(principal.getId());
        }
    }
}
