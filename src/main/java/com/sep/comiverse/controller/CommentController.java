package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterCommentDTO;
import com.sep.comiverse.dto.ComicCommentDTO;
import com.sep.comiverse.dto.request.CreateChapterCommentRequest;
import com.sep.comiverse.dto.request.CreateComicCommentRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
