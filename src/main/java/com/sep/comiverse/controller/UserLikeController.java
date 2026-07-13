package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.UserLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Tag(name = "User - Likes", description = "APIs for user likes on comics")
public class UserLikeController {

    private final UserLikeService userLikeService;
    private final com.sep.comiverse.security.JwtTokenUtil jwtTokenUtil;
    private final com.sep.comiverse.plugin.crud.ComicCrudPlugin comicCrudPlugin;

    @GetMapping("/my-likes")
    @Operation(summary = "Get list of liked comics", description = "Retrieve list of comics liked by the logged-in user, mapped via ComicCrudPlugin to get latest Redis stats")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> getLikedComics() {
        UUID userId = this.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(
                    BaseResponse.<List<com.sep.comiverse.dto.ComicDTO>>builder()
                            .success(true)
                            .data(Collections.emptyList())
                            .build()
            );
        }

        List<UUID> likedComicIds = userLikeService.getLikedComicIds(userId);
        List<com.sep.comiverse.dto.ComicDTO> comics = likedComicIds.stream()
                .map(comicCrudPlugin::getComicDetail)
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<java.util.List<com.sep.comiverse.dto.ComicDTO>>builder()
                        .success(true)
                        .data(comics)
                        .build()
        );
    }

    @PostMapping("/toggle/{comicId}")
    @Operation(summary = "Toggle like on a comic", description = "Like or unlike a comic for the currently logged-in user")
    public ResponseEntity<BaseResponse<Boolean>> toggleLike(
            @PathVariable UUID comicId
    ) {
        UUID userId = this.getCurrentUserId();
        boolean isLiked = userLikeService.toggleLikeComic(comicId, userId);
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
            @PathVariable UUID comicId
    ) {
        UUID userId = this.getCurrentUserId();
        boolean isLiked = userLikeService.isComicLikedByUser(comicId, userId);
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isLiked)
                        .build()
        );
    }

    private UUID getCurrentUserId(){
        return jwtTokenUtil.getCurrentUserId();
    }
}
