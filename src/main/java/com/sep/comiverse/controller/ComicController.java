package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/comics")
@RequiredArgsConstructor
public class ComicController {

    private final ComicCrudPlugin comicCrudPlugin;
    private final RecommendationService recommendationService;
    private final JwtTokenUtil jwtTokenUtil;
    private final com.sep.comiverse.service.AuditLogService auditLogService;
    private final com.sep.comiverse.service.NotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Retrieve a paginated public collection of published comics")
    public ResponseEntity<PaginationResponse<List<ComicDTO>>> findPublishedComics(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO
    ) {
        PaginationSearchDTO safePagination =
                paginationDTO != null ? paginationDTO : new PaginationSearchDTO();

        Page<ComicDTO> data = comicCrudPlugin.listPublishedComics(safePagination);

        return ResponseEntity.ok(PaginationResponse.<List<ComicDTO>>builder()
                .success(true)
                .metadata(new PaginationMetadata(
                        safePagination.getPage(),
                        safePagination.getSize(),
                        data.getTotalElements(),
                        data.getTotalPages()
                ))
                .data(data.toList())
                .build());
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Get recommended comics using vector similarity with cursor pagination")
    public ResponseEntity<BaseResponse<CursorResponseDTO<ComicDTO>>> getRecommendations(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID referenceId,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        CursorResponseDTO<UUID> idCursor = recommendationService.getRecommendedComicIdsCursor(userId, cursor, referenceId, size);

        List<ComicDTO> data = idCursor.getData().stream()
                .map(id -> {
                    try {
                        return comicCrudPlugin.getComicDetail(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        CursorResponseDTO<ComicDTO> response = new CursorResponseDTO<>(
                data,
                idCursor.getNextCursor(),
                idCursor.getNextReferenceId(),
                idCursor.isHasMore()
        );

        return ResponseEntity.ok(BaseResponse.<CursorResponseDTO<ComicDTO>>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Retrieve cached leaderboard by timeframe")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> getLeaderboard(
            @RequestParam(defaultValue = "day") String timeframe
    ) {
        List<ComicDTO> data = comicCrudPlugin.getCachedLeaderboard(timeframe);
        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(data)
                .build());
    }

    @GetMapping("/all")
    @Operation(summary = "Retrieve all published comics")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(comicCrudPlugin.listPublishedComics())
                .build());
    }

    @GetMapping("/staff/all")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR', 'TRANSLATOR', 'PROJECT_LEADER')")
    @Operation(summary = "Retrieve all comics (including un-published) for staff")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> listAllForStaff(org.springframework.security.core.Authentication authentication) {
        List<ComicDTO> all = comicCrudPlugin.listAllForStaff();

        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(all)
                .build());
    }


    @GetMapping("/explore")
    @Operation(summary = "Explore published catalog using optimized cursor pagination with filters and dynamic sorting")
    public ResponseEntity<BaseResponse<CursorResponseDTO<ComicDTO>>> getExploreComics(
            @Valid @ParameterObject ComicExploreRequestDTO request
    ) {
        ComicExploreRequestDTO safeRequest =
                request != null ? request : new ComicExploreRequestDTO();

        CursorResponseDTO<ComicDTO> result =
                comicCrudPlugin.getExploreComicsCursor(safeRequest);

        return ResponseEntity.ok(BaseResponse.<CursorResponseDTO<ComicDTO>>builder()
                .success(true)
                .data(result)
                .build());
    }

    /**
     * Public comic detail.
     *
     * Quan trọng:
     * comicCrudPlugin.getComicDetail(id) phải chỉ trả comic đã PUBLISHED.
     * Không được trả comic PENDING / DRAFT / REJECTED ra public.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get public comic detail")
    public ResponseEntity<BaseResponse<ComicDTO>> findById(
            @PathVariable
            @Parameter(required = true)
            UUID id
    ) {
        ComicDTO data = comicCrudPlugin.getComicDetail(id);

        return ResponseEntity.ok(BaseResponse.<ComicDTO>builder()
                .success(true)
                .data(data)
                .build());
    }

    /**
     * ADMIN CRUD - tạo comic trực tiếp.
     * Author nên tạo comic qua AuthorComicController.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ComicDTO>> create(
            @Valid @RequestBody ComicDTO dto
    ) {
        ComicDTO created = comicCrudPlugin.create(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ComicDTO>builder()
                        .success(true)
                        .data(created)
                        .build());
    }

    /**
     * ADMIN/MODERATOR CRUD - edit comic metadata.
     * Moderators can only edit: language, genres, minimumAge, publicationStatus.
     * Title, summary, cover are Author's intellectual property — Mod should Reject to request changes.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    public ResponseEntity<BaseResponse<ComicDTO>> update(
            @PathVariable UUID id,
            @RequestBody ComicDTO dto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    com.sep.comiverse.security.UserPrincipal principal
    ) {
        // Read existing comic BEFORE update for change detection
        ComicDTO before = comicCrudPlugin.read(id).orElse(null);

        boolean isModerator = principal != null && "MODERATOR".equalsIgnoreCase(principal.getRole());

        // Security guardrail: strip Author-owned fields if caller is Moderator
        if (isModerator) {
            dto.setTitle(null);
            dto.setSummary(null);
            dto.setCover(null);
            
            // Set fields for the Author to review later
            if (before != null) {
                try {
                    dto.setIsModEdited(true);
                    if (Boolean.TRUE.equals(before.getIsModEdited()) && before.getPreviousStateSnapshot() != null) {
                        dto.setPreviousStateSnapshot(before.getPreviousStateSnapshot());
                    } else {
                        dto.setPreviousStateSnapshot(objectMapper.writeValueAsString(before));
                    }
                } catch (Exception e) {
                    // Ignore mapping error
                }
            }
        }

        ComicDTO updated = comicCrudPlugin.update(id, dto);

        // Build detailed change log
        String modName = principal != null
                ? (principal.getFullName() != null ? principal.getFullName() : principal.getUsername())
                : "Staff";
        StringBuilder changes = new StringBuilder();
        if (before != null) {
            if (dto.getLanguage() != null && !dto.getLanguage().equals(before.getLanguage())) {
                changes.append("Language (").append(before.getLanguage()).append(" → ").append(dto.getLanguage()).append("), ");
            }
            if (dto.getMinimumAge() != null && !dto.getMinimumAge().equals(before.getMinimumAge())) {
                changes.append("Age Rating (").append(before.getMinimumAge()).append(" → ").append(dto.getMinimumAge()).append("), ");
            }
            if (dto.getPublicationStatus() != null && !dto.getPublicationStatus().equals(before.getPublicationStatus())) {
                changes.append("Publication Status (").append(before.getPublicationStatus()).append(" → ").append(dto.getPublicationStatus()).append("), ");
            }
            if (dto.getGenreIds() != null) {
                changes.append("Genres updated, ");
            }
            // Non-moderator (Admin) changes to title/summary
            if (!isModerator) {
                if (dto.getTitle() != null && !dto.getTitle().equals(before.getTitle())) {
                    changes.append("Title (").append(before.getTitle()).append(" → ").append(dto.getTitle()).append("), ");
                }
            }
        }
        String changeDesc = changes.length() > 2
                ? changes.substring(0, changes.length() - 2)
                : "metadata";
        auditLogService.log("COMIC_MANAGEMENT",
                modName + " updated " + updated.getTitle() + ": " + changeDesc);

        // Notify the Author about the edit (only when a Moderator edits someone else's comic)
        if (isModerator && before != null && before.getAuthorId() != null && changes.length() > 0) {
            String reason = dto.getRejectionReason();
            String notifTitle = "Comic metadata updated by Moderator";
            String notifMessage = "Moderator " + modName + " updated your comic \"" + updated.getTitle() + "\": " + changeDesc + ".";
            if (reason != null && !reason.isBlank()) {
                notifMessage += " Reason: " + reason.trim();
            }
            try {
                notificationService.notifyUser(
                        before.getAuthorId(),
                        notifTitle,
                        notifMessage,
                        "UPDATE",
                        "/author/comics/" + updated.getId() + "?appeal=true",
                        com.sep.comiverse.entity.enums.NotificationPreferenceKey.SUBMISSION_STATUS
                );
            } catch (Exception e) {
                // Non-critical — don't fail the update if notification fails
            }
        }

        return ResponseEntity.ok(BaseResponse.<ComicDTO>builder()
                .success(true)
                .data(updated)
                .build());
    }

    /**
     * ADMIN/MODERATOR CRUD - xóa comic trực tiếp.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        String title = "Unknown Title";
        try {
            title = comicCrudPlugin.read(id).map(ComicDTO::getTitle).orElse("Unknown Title");
        } catch (Exception e) {
            // ignore
        }
        comicCrudPlugin.delete(id);
        auditLogService.log("COMIC_MANAGEMENT", "Archived comic profile: " + title);

        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .build());
    }

    /**
     * ADMIN detail - dùng khi admin cần xem cả comic chưa duyệt.
     * Tách riêng để không trùng với public GET /comics/{id}.
     */
    @GetMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get comic detail for admin")
    public ResponseEntity<BaseResponse<ComicDTO>> findByIdForAdmin(
            @PathVariable UUID id
    ) {
        var comicOptional = comicCrudPlugin.read(id);

        if (comicOptional.isPresent()) {
            BaseResponse<ComicDTO> response = BaseResponse.<ComicDTO>builder()
                    .success(true)
                    .data(comicOptional.get())
                    .build();
            return ResponseEntity.ok(response);
        }

        BaseResponse<ComicDTO> errorResponse = BaseResponse.<ComicDTO>builder()
                .success(false)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}