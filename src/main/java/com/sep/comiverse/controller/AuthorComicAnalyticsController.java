package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ComicMetricsResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorComicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/author/comics/{comicId}/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Comic Analytics", description = "APIs for author comic metrics")
public class AuthorComicAnalyticsController {

    private final AuthorComicService authorComicService;

    @GetMapping
    @Operation(summary = "Get comic metrics", description = "Returns the latest metric snapshot for one comic owned by the authenticated author")
    public ResponseEntity<BaseResponse<ComicMetricsResponse>> getComicMetrics(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = principal != null ? principal.getId() : authorId;
        return ResponseEntity.ok(BaseResponse.<ComicMetricsResponse>builder()
                .success(true)
                .data(authorComicService.getComicMetrics(comicId, resolvedAuthorId))
                .build());
    }
}
