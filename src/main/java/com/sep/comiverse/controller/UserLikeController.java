package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.UserLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Tag(name = "User - Likes", description = "APIs for user likes on comics")
public class UserLikeController {

    private final UserLikeService userLikeService;

    @PostMapping("/toggle/{comicId}")
    @Operation(summary = "Toggle like on a comic", description = "Like or unlike a comic for the currently logged-in user")
    public ResponseEntity<BaseResponse<Boolean>> toggleLike(
            @PathVariable UUID comicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isLiked = userLikeService.toggleLikeComic(comicId, principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isLiked)
                        .message(isLiked ? "Comic liked successfully" : "Comic unliked successfully")
                        .build()
        );
    }

    @GetMapping("/check/{comicId}")
    @Operation(summary = "Check if user liked a comic")
    public ResponseEntity<BaseResponse<Boolean>> checkLike(
            @PathVariable UUID comicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isLiked = userLikeService.isComicLikedByUser(comicId, principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isLiked)
                        .build()
        );
    }
}
