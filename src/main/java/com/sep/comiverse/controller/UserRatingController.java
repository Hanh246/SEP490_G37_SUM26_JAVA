package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.request.RateComicRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ComicRatingResponse;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.UserRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@Tag(name = "User - Ratings", description = "APIs for user ratings on comics")
public class UserRatingController {

    private final UserRatingService userRatingService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ComicCrudPlugin comicCrudPlugin;

    @PostMapping("/comics/{comicId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rate or update rating for a comic", description = "Submit a 1 to 5 star rating for a comic. Updates existing rating if already rated.")
    public ResponseEntity<BaseResponse<ComicRatingResponse>> rateComic(
            @PathVariable UUID comicId,
            @Valid @RequestBody RateComicRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ComicRatingResponse response = userRatingService.rateComic(comicId, userId, request.getScore());

        return ResponseEntity.ok(
                BaseResponse.<ComicRatingResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Comic rated successfully")
                        .build()
        );
    }

    @DeleteMapping("/comics/{comicId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete rating for a comic", description = "Remove logged-in user's rating for a comic.")
    public ResponseEntity<BaseResponse<ComicRatingResponse>> deleteRating(
            @PathVariable UUID comicId
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ComicRatingResponse response = userRatingService.deleteRating(comicId, userId);

        return ResponseEntity.ok(
                BaseResponse.<ComicRatingResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Comic rating deleted successfully")
                        .build()
        );
    }

    @GetMapping("/comics/{comicId}")
    @Operation(summary = "Get comic rating stats and current user score", description = "Retrieve average rating score, total count, and user's score if authenticated.")
    public ResponseEntity<BaseResponse<ComicRatingResponse>> getComicRating(
            @PathVariable UUID comicId
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        ComicRatingResponse response = userRatingService.getComicRating(comicId, userId);

        return ResponseEntity.ok(
                BaseResponse.<ComicRatingResponse>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/my-ratings")
    @Operation(summary = "Get list of comics rated by the logged-in user", description = "Retrieve comics rated by user, mapped via ComicCrudPlugin to get latest stats.")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> getMyRatedComics() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(
                    BaseResponse.<List<ComicDTO>>builder()
                            .success(true)
                            .data(Collections.emptyList())
                            .build()
            );
        }

        List<UUID> ratedComicIds = userRatingService.getRatedComicIds(userId);
        List<ComicDTO> comics = ratedComicIds.stream()
                .map(comicCrudPlugin::getComicDetail)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<List<ComicDTO>>builder()
                        .success(true)
                        .data(comics)
                        .build()
        );
    }
}
