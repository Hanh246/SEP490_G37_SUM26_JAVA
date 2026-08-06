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

    @GetMapping
    @Operation(summary = "Get comments for a forum thread")
    public ResponseEntity<BaseResponse<List<ForumCommentDTO>>> getComments(@PathVariable UUID threadId) {
        return ResponseEntity.ok(BaseResponse.<List<ForumCommentDTO>>builder()
                .success(true)
                .data(forumCommentService.getComments(threadId))
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
        ForumCommentDTO created = forumCommentService.createComment(threadId, request, principal.getId());
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
        ForumCommentDTO updated = forumCommentService.updateComment(commentId, request, principal.getId());
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
        forumCommentService.deleteComment(commentId, principal.getId(), principal.getRole());
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Forum comment deleted successfully")
                .build());
    }
}
