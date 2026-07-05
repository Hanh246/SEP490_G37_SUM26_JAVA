package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comics")
@RequiredArgsConstructor
public class ComicController {

    private final ComicCrudPlugin comicCrudPlugin;

    @GetMapping
    @Operation(summary = "Retrieve a paginated public collection of published comics")
    public ResponseEntity<PaginationResponse<List<ComicDTO>>> findPublishedComics(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO) {
        PaginationSearchDTO safePagination = paginationDTO != null ? paginationDTO : new PaginationSearchDTO();
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

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(comicCrudPlugin.listPublishedComics())
                .build());
    }

    @GetMapping("/top-views")
    @Operation(summary = "Retrieve a paginated collection of published comics sorted by maximum traffic views")
    public ResponseEntity<PaginationResponse<List<ComicDTO>>> getTopViews(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO) {
        PaginationSearchDTO safePagination = paginationDTO != null ? paginationDTO : new PaginationSearchDTO();
        Page<ComicDTO> data = comicCrudPlugin.getTopViews(safePagination);

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

    @GetMapping("/recently-updated")
    @Operation(summary = "Retrieve a paginated collection of published comics with recently published chapters")
    public ResponseEntity<PaginationResponse<List<ComicDTO>>> getRecentlyUpdated(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO) {
        PaginationSearchDTO safePagination = paginationDTO != null ? paginationDTO : new PaginationSearchDTO();
        Page<ComicDTO> data = comicCrudPlugin.getComicsByLatestChapters(safePagination);

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

    @GetMapping("/explore")
    @Operation(summary = "Explore published catalog using optimized cursor pagination with filters and dynamic sorting")
    public ResponseEntity<BaseResponse<CursorResponseDTO<ComicDTO>>> getExploreComics(
            @Valid @ParameterObject ComicExploreRequestDTO request) {
        ComicExploreRequestDTO safeRequest = request != null ? request : new ComicExploreRequestDTO();
        CursorResponseDTO<ComicDTO> result = comicCrudPlugin.getExploreComicsCursor(safeRequest);

        return ResponseEntity.ok(BaseResponse.<CursorResponseDTO<ComicDTO>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ComicDTO>> create(@Valid @RequestBody ComicDTO dto) {
        ComicDTO created = comicCrudPlugin.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ComicDTO>builder().success(true).data(created).build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ComicDTO>> findById(@PathVariable UUID id) {
        return comicCrudPlugin.read(id)
                .map(dto -> ResponseEntity.ok(BaseResponse.<ComicDTO>builder().success(true).data(dto).build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponse.<ComicDTO>builder().success(false).build()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ComicDTO>> update(@PathVariable UUID id, @RequestBody ComicDTO dto) {
        ComicDTO updated = comicCrudPlugin.update(id, dto);
        return ResponseEntity.ok(BaseResponse.<ComicDTO>builder().success(true).data(updated).build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        comicCrudPlugin.delete(id);
        return ResponseEntity.ok(BaseResponse.<Void>builder().success(true).build());
    }
}
