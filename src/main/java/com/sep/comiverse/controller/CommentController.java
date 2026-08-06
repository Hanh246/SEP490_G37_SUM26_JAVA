package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterCommentDTO;
import com.sep.comiverse.dto.ComicCommentDTO;
import com.sep.comiverse.dto.request.CreateChapterCommentRequest;
import com.sep.comiverse.dto.request.CreateComicCommentRequest;
import com.sep.comiverse.dto.request.UpdateChapterCommentRequest;
import com.sep.comiverse.dto.request.UpdateComicCommentRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sep.comiverse.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "Comment API", description = "Endpoints for user comments on Comics and Chapters")
public class CommentController {

    private final CommentService commentService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/comics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a comment on a Comic")
    public ResponseEntity<BaseResponse<ComicCommentDTO>> createComicComment(
            @Valid @RequestBody CreateComicCommentRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ComicCommentDTO responseData = commentService.createComicComment(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ComicCommentDTO>builder()
                        .success(true)
                        .data(responseData)
                        .message("Comic comment created successfully")
                        .build());
    }

    @PostMapping("/chapters")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a comment on a Chapter")
    public ResponseEntity<BaseResponse<ChapterCommentDTO>> createChapterComment(
            @Valid @RequestBody CreateChapterCommentRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ChapterCommentDTO responseData = commentService.createChapterComment(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ChapterCommentDTO>builder()
                        .success(true)
                        .data(responseData)
                        .message("Chapter comment created successfully")
                        .build());
    }

    @GetMapping("/comics")
    @Operation(summary = "Get comments of a Comic by comicId and parentId with pagination")
    public ResponseEntity<PaginationResponse<List<ComicCommentDTO>>> getComicComments(
            @RequestParam("comicId") UUID comicId,
            @RequestParam(value = "parentId", required = false) UUID parentId,
            @Valid @ParameterObject PaginationSearchDTO paginationSearchDTO
    ) {
        PaginationSearchDTO safePagination =
                paginationSearchDTO != null ? paginationSearchDTO : new PaginationSearchDTO();

        Page<ComicCommentDTO> data = commentService.getComicComments(comicId, parentId, safePagination.toPageRequest());

        return ResponseEntity.ok(PaginationResponse.<List<ComicCommentDTO>>builder()
                .success(true)
                .metadata(new PaginationMetadata(
                        safePagination.getPage(),
                        safePagination.getSize(),
                        data.getTotalElements(),
                        data.getTotalPages()
                ))
                .data(data.toList())
                .build());
    }

    @GetMapping("/chapters")
    @Operation(summary = "Get comments of a Chapter by chapterId and parentId with pagination")
    public ResponseEntity<PaginationResponse<List<ChapterCommentDTO>>> getChapterComments(
            @RequestParam("chapterId") UUID chapterId,
            @RequestParam(value = "parentId", required = false) UUID parentId,
            @Valid @ParameterObject PaginationSearchDTO paginationSearchDTO
    ) {
        PaginationSearchDTO safePagination =
                paginationSearchDTO != null ? paginationSearchDTO : new PaginationSearchDTO();

        Page<ChapterCommentDTO> data = commentService.getChapterComments(chapterId, parentId, safePagination.toPageRequest());

        return ResponseEntity.ok(PaginationResponse.<List<ChapterCommentDTO>>builder()
                .success(true)
                .metadata(new PaginationMetadata(
                        safePagination.getPage(),
                        safePagination.getSize(),
                        data.getTotalElements(),
                        data.getTotalPages()
                ))
                .data(data.toList())
                .build());
    }

    @GetMapping("/comics/{id}")
    @Operation(summary = "Get comic comment thread by comment ID")
    public ResponseEntity<BaseResponse<List<ComicCommentDTO>>> getComicCommentThread(
            @PathVariable("id") UUID id
    ) {
        List<ComicCommentDTO> responseData = commentService.getComicCommentThreadById(id);
        return ResponseEntity.ok(BaseResponse.<List<ComicCommentDTO>>builder()
                .success(true)
                .data(responseData)
                .message("Comic comment thread retrieved successfully")
                .build());
    }

    @GetMapping("/chapters/{id}")
    @Operation(summary = "Get chapter comment thread by comment ID")
    public ResponseEntity<BaseResponse<List<ChapterCommentDTO>>> getChapterCommentThread(
            @PathVariable("id") UUID id
    ) {
        List<ChapterCommentDTO> responseData = commentService.getChapterCommentThreadById(id);
        return ResponseEntity.ok(BaseResponse.<List<ChapterCommentDTO>>builder()
                .success(true)
                .data(responseData)
                .message("Chapter comment thread retrieved successfully")
                .build());
    }

    @PutMapping("/comics/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a Comic comment")
    public ResponseEntity<BaseResponse<ComicCommentDTO>> updateComicComment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateComicCommentRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ComicCommentDTO responseData = commentService.updateComicComment(id, request, userId);

        return ResponseEntity.ok(BaseResponse.<ComicCommentDTO>builder()
                .success(true)
                .data(responseData)
                .message("Comic comment updated successfully")
                .build());
    }

    @PutMapping("/chapters/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a Chapter comment")
    public ResponseEntity<BaseResponse<ChapterCommentDTO>> updateChapterComment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChapterCommentRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ChapterCommentDTO responseData = commentService.updateChapterComment(id, request, userId);

        return ResponseEntity.ok(BaseResponse.<ChapterCommentDTO>builder()
                .success(true)
                .data(responseData)
                .message("Chapter comment updated successfully")
                .build());
    }

    @DeleteMapping("/comics/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a comic comment by ID")
    public ResponseEntity<BaseResponse<Void>> deleteComicComment(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        String role = principal != null ? principal.getRole() : null;
        commentService.deleteComicComment(id, userId, role);

        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Comic comment deleted successfully")
                .build());
    }

    @DeleteMapping("/chapters/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a chapter comment by ID")
    public ResponseEntity<BaseResponse<Void>> deleteChapterComment(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        String role = principal != null ? principal.getRole() : null;
        commentService.deleteChapterComment(id, userId, role);

        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Chapter comment deleted successfully")
                .build());
    }
}
