package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.UserSaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/saves")
@RequiredArgsConstructor
@Tag(name = "User - Saves", description = "APIs for user bookmarks/saves on comics")
public class UserSaveController {

    private final UserSaveService userSaveService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ComicCrudPlugin comicCrudPlugin;

    @GetMapping("/my-saves")
    @Operation(summary = "Get list of saved/bookmarked comics", description = "Retrieve list of comics saved/bookmarked by the logged-in user, mapped via ComicCrudPlugin to get latest Redis stats")
    public ResponseEntity<BaseResponse<java.util.List<com.sep.comiverse.dto.ComicDTO>>> getSavedComics() {
        UUID userId = this.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(
                    BaseResponse.<java.util.List<com.sep.comiverse.dto.ComicDTO>>builder()
                            .success(true)
                            .data(java.util.Collections.emptyList())
                            .build()
            );
        }

        List<UUID> savedComicIds = userSaveService.getSavedComicIds(userId);
        List<com.sep.comiverse.dto.ComicDTO> comics = savedComicIds.stream()
                .map(comicCrudPlugin::getComicDetail)
                .collect(Collectors.toList());

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
            @PathVariable UUID comicId
    ) {
        UUID userId = this.getCurrentUserId();
        boolean isSaved = userSaveService.toggleSaveComic(comicId, userId);
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
            @PathVariable UUID comicId
    ) {
        UUID userId = this.getCurrentUserId();
        boolean isSaved = userSaveService.isComicSavedByUser(comicId, userId);
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(isSaved)
                        .build()
        );
    }

    @GetMapping("/count")
    @Operation(summary = "Get total count of saved comics", description = "Retrieve total count of comics saved/bookmarked by the logged-in user")
    public ResponseEntity<BaseResponse<Long>> getSavedComicCount() {
        UUID userId = this.getCurrentUserId();
        long count = userSaveService.getSavedComicCount(userId);
        return ResponseEntity.ok(
                BaseResponse.<Long>builder()
                        .success(true)
                        .data(count)
                        .build()
        );
    }

    private UUID getCurrentUserId(){
        return jwtTokenUtil.getCurrentUserId();
    }
}
