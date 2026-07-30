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

        if (authentication != null && authentication.getPrincipal() instanceof com.sep.comiverse.security.UserPrincipal principal) {
            com.sep.comiverse.entity.UserEntity user = principal.user();
            if ("MODERATOR".equalsIgnoreCase(user.getRole().getRoleName())) {
                String langs = user.getAssignedLanguages();
                if (langs != null && !langs.isBlank()) {
                    List<String> scope = java.util.Arrays.stream(langs.toLowerCase().split(","))
                            .map(String::trim).toList();
                    all = all.stream().filter(c -> {
                        String comicLang = c.getLanguage();
                        if (comicLang == null) return false;
                        return scope.stream().anyMatch(l -> comicLang.toLowerCase().contains(l) || l.contains(comicLang.toLowerCase()));
                    }).toList();
                } else {
                    all = java.util.Collections.emptyList();
                }
            }
        }

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
     * ADMIN CRUD - sửa comic trực tiếp.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    public ResponseEntity<BaseResponse<ComicDTO>> update(
            @PathVariable UUID id,
            @RequestBody ComicDTO dto
    ) {
        ComicDTO updated = comicCrudPlugin.update(id, dto);
        auditLogService.log("COMIC_MANAGEMENT", "Updated comic metadata: " + updated.getTitle());

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