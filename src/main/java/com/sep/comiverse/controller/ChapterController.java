package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
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
@RequestMapping("/chapters")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class ChapterController {

    private final ChapterCrudPlugin chapterCrudPlugin;

    @PostMapping
    public ResponseEntity<BaseResponse<ChapterDTO>> create(@Valid @RequestBody ChapterDTO dto) {
        ChapterDTO created = chapterCrudPlugin.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ChapterDTO>builder().success(true).data(created).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ChapterDTO>> findById(@PathVariable UUID id) {
        return chapterCrudPlugin.read(id)
                .map(dto -> ResponseEntity.ok(BaseResponse.<ChapterDTO>builder().success(true).data(dto).build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponse.<ChapterDTO>builder().success(false).build()));
    }

    @GetMapping
    public ResponseEntity<PaginationResponse<List<ChapterDTO>>> findAll(@Valid @ParameterObject PaginationSearchDTO paginationDTO) {
        PaginationSearchDTO safePagination = paginationDTO != null ? paginationDTO : new PaginationSearchDTO();
        Page<ChapterDTO> data = chapterCrudPlugin.list(safePagination);
        return ResponseEntity.ok(PaginationResponse.<List<ChapterDTO>>builder()
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

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ChapterDTO>> update(@PathVariable UUID id, @RequestBody ChapterDTO dto) {
        ChapterDTO updated = chapterCrudPlugin.update(id, dto);
        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder().success(true).data(updated).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        chapterCrudPlugin.delete(id);
        return ResponseEntity.ok(BaseResponse.<Void>builder().success(true).build());
    }
}
