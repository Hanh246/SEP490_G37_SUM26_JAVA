package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorComicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/author/comics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Comics", description = "APIs for author comic creation and management")
public class AuthorComicController {

    private final AuthorComicService authorComicService;

    @PostMapping
    @Operation(summary = "Create a new comic draft", description = "Creates a comic profile with cover information only. The comic remains DRAFT until the author submits it for review.")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> createComic(
            @Valid @RequestBody AuthorComicCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        AuthorComicResponse data = authorComicService.createComic(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<AuthorComicResponse>builder()
                        .success(true)
                        .message("Comic draft created successfully")
                        .data(data)
                        .build());
    }



    @PostMapping("/{comicId}/submit-review")
    @Operation(summary = "Submit comic for review", description = "Submits an owned comic profile to moderation. The comic must contain at least one non-deleted chapter.")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> submitComicForReview(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorComicResponse>builder()
                .success(true)
                .message("Comic submitted for moderator review")
                .data(authorComicService.submitForReview(comicId, resolvedAuthorId))
                .build());
    }

    @PutMapping("/{comicId}/confirm-edit")
    @Operation(summary = "Confirm moderator edits", description = "Allows the author to confirm and accept moderator edits, clearing the notice.")
    public ResponseEntity<BaseResponse<Void>> confirmModEdit(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        authorComicService.confirmModEdit(comicId, resolvedAuthorId);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Edits confirmed")
                .build());
    }

    @PostMapping("/{comicId}/appeal")
    @Operation(summary = "Submit an appeal / dispute for a comic", description = "Allows the author to submit an appeal regarding moderation changes or rejection.")
    public ResponseEntity<BaseResponse<Void>> submitAppeal(
            @PathVariable UUID comicId,
            @Valid @RequestBody com.sep.comiverse.dto.request.AuthorComicAppealRequest request,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        authorComicService.submitAppeal(comicId, resolvedAuthorId, request);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Appeal submitted successfully! Moderators have been notified.")
                .build());
    }

    @GetMapping
    @Operation(summary = "List own comics", description = "Returns comics owned by the authenticated author")
    public ResponseEntity<PaginationResponse<List<AuthorComicResponse>>> listOwnComics(
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @Valid @ParameterObject PaginationSearchDTO pagination,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        Page<AuthorComicResponse> data = authorComicService.listOwnComics(resolvedAuthorId, safePagination);
        return ResponseEntity.ok(
                PaginationResponse.<List<AuthorComicResponse>>builder()
                        .success(true)
                        .data(data.getContent())
                        .metadata(new PaginationMetadata(
                                safePagination.getPage(),
                                safePagination.getSize(),
                                data.getTotalElements(),
                                data.getTotalPages()
                        ))
                        .build()
        );
    }

    @GetMapping("/{comicId}")
    @Operation(summary = "Get own comic detail", description = "Returns detail for one comic owned by the authenticated author")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> getComic(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorComicResponse>builder()
                .success(true)
                .data(authorComicService.getComic(comicId, resolvedAuthorId))
                .build());
    }

    @PutMapping("/{comicId}")
    @Operation(summary = "Update own comic", description = "Updates editable comic information for the authenticated author")
    public ResponseEntity<BaseResponse<AuthorComicResponse>> updateComic(
            @PathVariable UUID comicId,
            @Valid @RequestBody AuthorComicUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applyPrincipalAuthorId(request, principal);
        return ResponseEntity.ok(BaseResponse.<AuthorComicResponse>builder()
                .success(true)
                .message("Comic information updated")
                .data(authorComicService.updateComic(comicId, request))
                .build());
    }


    @DeleteMapping("/{comicId}")
    @Operation(summary = "Soft-delete own comic", description = "Marks an owned comic as deleted so it is removed from author/public listings")
    public ResponseEntity<BaseResponse<Void>> deleteComic(
            @PathVariable UUID comicId,
            @RequestParam(value = "authorId", required = false) UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID resolvedAuthorId = resolveAuthorId(authorId, principal);
        authorComicService.deleteComic(comicId, resolvedAuthorId);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Comic deleted")
                .build());
    }

    private UUID resolveAuthorId(UUID requestAuthorId, UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        return requestAuthorId;
    }

    private void applyPrincipalAuthorId(AuthorComicCreateRequest request, UserPrincipal principal) {
        if (request != null && principal != null) {
            request.setAuthorId(principal.getId());
        }
    }

    private void applyPrincipalAuthorId(AuthorComicUpdateRequest request, UserPrincipal principal) {
        if (request != null && principal != null) {
            request.setAuthorId(principal.getId());
        }
    }
}
