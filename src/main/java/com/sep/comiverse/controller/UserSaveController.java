package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.UserSaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/saves")
@RequiredArgsConstructor
@Tag(name = "User - Saves", description = "APIs for user bookmarks/saves on comics")
public class UserSaveController {

    private final UserSaveService userSaveService;
    private final com.sep.comiverse.security.JwtTokenUtil jwtTokenUtil;
    private final com.sep.comiverse.plugin.crud.ComicCrudPlugin comicCrudPlugin;

    @GetMapping("/my-saves")
    @Operation(summary = "Get list of saved/bookmarked comics", description = "Retrieve list of comics saved/bookmarked by the logged-in user, mapped via ComicCrudPlugin to get latest Redis stats")
    public ResponseEntity<BaseResponse<java.util.List<com.sep.comiverse.dto.ComicDTO>>> getSavedComics() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(
                    BaseResponse.<java.util.List<com.sep.comiverse.dto.ComicDTO>>builder()
                            .success(true)
                            .data(java.util.Collections.emptyList())
                            .build()
            );
        }

        java.util.List<UUID> savedComicIds = userSaveService.getSavedComicIds(userId);
        java.util.List<com.sep.comiverse.dto.ComicDTO> comics = savedComicIds.stream()
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
    @Operation(summary = "Toggle save/bookmark on a comic", description = "Bookmark or remove bookmark for the currently logged-in user")
    public ResponseEntity<BaseResponse<Boolean>> toggleSave(
            @PathVariable UUID comicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isSaved = userSaveService.toggleSaveComic(comicId, principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isSaved)
                        .message(isSaved ? "Comic saved successfully" : "Comic unsaved successfully")
                        .build()
        );
    }

    @GetMapping("/check/{comicId}")
    @Operation(summary = "Check if user saved a comic")
    public ResponseEntity<BaseResponse<Boolean>> checkSave(
            @PathVariable UUID comicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isSaved = userSaveService.isComicSavedByUser(comicId, principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isSaved)
                        .build()
        );
    }
}
