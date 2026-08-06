package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ForumCommentDTO;
import com.sep.comiverse.dto.request.CreateForumCommentRequest;
import com.sep.comiverse.dto.request.UpdateForumCommentRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ForumCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/forum-threads/{threadId}/comments")
@Tag(name = "Forum Comments", description = "Comments and replies for forum discussion threads")
public class ForumCommentController {

    private final ForumCommentService forumCommentService;
    private final com.sep.comiverse.security.JwtTokenUtil jwtTokenUtil;

    @GetMapping
    @Operation(summary = "Get comments for a forum thread")
    public ResponseEntity<BaseResponse<List<ForumCommentDTO>>> getComments(
            @PathVariable UUID threadId
    ) {
        UUID currentUserId = null;
        try {
            currentUserId = jwtTokenUtil.getCurrentUserId();
        } catch (Exception ignored) {
            // Anonymous user — no liked status needed
        }
        return ResponseEntity.ok(BaseResponse.<List<ForumCommentDTO>>builder()
                .success(true)
                .data(forumCommentService.getComments(threadId, currentUserId))
                .build());
    }

    @PostMapping("/{commentId}/like")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Toggle like for a forum comment")
    public ResponseEntity<BaseResponse<Boolean>> toggleCommentLike(
            @PathVariable UUID threadId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        boolean isLiked = forumCommentService.toggleCommentLike(commentId, userId);
        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .success(true)
                .data(isLiked)
                .message(isLiked ? "Comment liked" : "Comment unliked")
                .build());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a comment or reply in a forum thread")
    public ResponseEntity<BaseResponse<ForumCommentDTO>> createComment(
            @PathVariable UUID threadId,
            @Valid @RequestBody CreateForumCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        ForumCommentDTO created = forumCommentService.createComment(threadId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ForumCommentDTO>builder()
                        .success(true)
                        .data(created)
                        .message("Forum reply created successfully")
                        .build());
    }

    @PutMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a forum comment")
    public ResponseEntity<BaseResponse<ForumCommentDTO>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateForumCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        ForumCommentDTO updated = forumCommentService.updateComment(commentId, request, userId);
        return ResponseEntity.ok(BaseResponse.<ForumCommentDTO>builder()
                .success(true)
                .data(updated)
                .message("Forum comment updated successfully")
                .build());
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a forum comment")
    public ResponseEntity<BaseResponse<Void>> deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : jwtTokenUtil.getCurrentUserId();
        String role = principal != null ? principal.getRole() : null;
        forumCommentService.deleteComment(commentId, userId, role);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Forum comment deleted successfully")
                .build());
    }
}
