package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.ReadingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reading-histories")
@RequiredArgsConstructor
@Tag(name = "User - Reading History", description = "APIs for user reading history on comics/chapters")
public class ReadingHistoryController {

    private final ReadingHistoryService readingHistoryService;
    private final IReadingHistoryRepository readingHistoryRepository;
    private final ComicCrudPlugin comicCrudPlugin;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/chapters/{comicId}")
    @Operation(summary = "Get list of read chapter IDs for a comic", description = "Retrieve all chapter IDs of a comic that the logged-in user has read")
    public ResponseEntity<BaseResponse<List<UUID>>> getReadChapters(@PathVariable UUID comicId) {
        List<UUID> chapterIds = readingHistoryService.getReadChapters(comicId);
        return ResponseEntity.ok(
                BaseResponse.<List<UUID>>builder()
                        .success(true)
                        .data(chapterIds)
                        .build()
        );
    }

    @GetMapping("/my-history")
    @Operation(summary = "Get list of read comics", description = "Retrieve list of comics that the logged-in user has read, sorted by latest read time, mapped via ComicCrudPlugin to get latest Redis stats")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> getReadingHistory() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(
                    BaseResponse.<List<ComicDTO>>builder()
                            .success(true)
                            .data(Collections.emptyList())
                            .build()
            );
        }

        List<UUID> readComicIds = readingHistoryRepository.findReadComicIdsByUserId(userId);
        List<ComicDTO> comics = readComicIds.stream()
                .map(comicCrudPlugin::getComicDetail)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                BaseResponse.<List<ComicDTO>>builder()
                        .success(true)
                        .data(comics)
                        .build()
        );
    }

    @DeleteMapping("/comic/{comicId}")
    @Operation(summary = "Delete comic reading history", description = "Delete the reading history of a comic, including all its read chapters, for the logged-in user")
    public ResponseEntity<BaseResponse<Boolean>> deleteComicHistory(@PathVariable UUID comicId) {
        readingHistoryService.deleteComicHistory(comicId);
        return ResponseEntity.ok(
                BaseResponse.<Boolean>builder()
                        .success(true)
                        .data(true)
                        .build()
        );
    }

    @GetMapping("/count")
    @Operation(summary = "Get count of read comics", description = "Retrieve total count of unique comics read by the logged-in user")
    public ResponseEntity<BaseResponse<Long>> getReadComicCount() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        long count = readingHistoryService.getReadComicCount(userId);
        return ResponseEntity.ok(
                BaseResponse.<Long>builder()
                        .success(true)
                        .data(count)
                        .build()
        );
    }
}
