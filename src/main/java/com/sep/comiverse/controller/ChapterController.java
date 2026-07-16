package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
public class ChapterController {

    private final ChapterCrudPlugin chapterCrudPlugin;
    private final JwtTokenUtil jwtTokenUtil;
    
    public ChapterController(ChapterCrudPlugin chapterCrudPlugin, JwtTokenUtil jwtTokenUtil) {
        this.chapterCrudPlugin = chapterCrudPlugin;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * ADMIN CRUD - tạo chapter trực tiếp.
     * Bình thường author nên upload qua AuthorChapterController,
     * không nên cho user thường gọi endpoint này.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> create(@Valid @RequestBody ChapterDTO dto) {
        ChapterDTO created = chapterCrudPlugin.create(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ChapterDTO>builder()
                        .success(true)
                        .data(created)
                        .build());
    }

    /**
     * ADMIN CRUD - đọc chapter bất kỳ theo id.
     * Endpoint này dành cho admin, không dùng cho reader public.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> findById(@PathVariable UUID id) {
        return chapterCrudPlugin.read(id)
                .<ResponseEntity<BaseResponse<ChapterDTO>>>map(dto -> ResponseEntity.ok(
                        BaseResponse.<ChapterDTO>builder()
                                .success(true)
                                .data(dto)
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BaseResponse.<ChapterDTO>builder()
                                .success(false)
                                .build()));
    }

    /**
     * ADMIN CRUD - danh sách tất cả chapter.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaginationResponse<List<ChapterDTO>>> findAll(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO
    ) {
        PaginationSearchDTO safePagination =
                paginationDTO != null ? paginationDTO : new PaginationSearchDTO();

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

    /**
     * ADMIN CRUD - sửa chapter trực tiếp.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> update(
            @PathVariable UUID id,
            @RequestBody ChapterDTO dto
    ) {
        ChapterDTO updated = chapterCrudPlugin.update(id, dto);

        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                .success(true)
                .data(updated)
                .build());
    }

    /**
     * ADMIN CRUD - xóa chapter trực tiếp.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        chapterCrudPlugin.delete(id);

        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .build());
    }

    /**
     * Reader/Frontend đọc nội dung chapter.
     * Quan trọng: trong getChapterDetail phải lọc chapter moderationStatus = PUBLISHED
     * nếu user không phải ADMIN/MODERATOR/owner author.
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "Get chapter detail")
    public ResponseEntity<BaseResponse<ChapterDTO>> getChapterDetail(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        String clientIp = request.getRemoteAddr();

        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                .success(true)
                .data(chapterCrudPlugin.getChapterDetail(id, userId, clientIp))
                .build());
    }

    /**
     * Reader/Frontend lấy danh sách chapter của comic.
     * Quan trọng: trong getChaptersByComicId phải chỉ trả chapter PUBLISHED.
     */
    @GetMapping("/comic/{comicId}")
    @Operation(summary = "Get list of chapters by comic ID")
    public ResponseEntity<BaseResponse<List<ChapterLiteDTO>>> getChaptersByComicId(
            @PathVariable UUID comicId
    ) {
        return ResponseEntity.ok(BaseResponse.<List<ChapterLiteDTO>>builder()
                .success(true)
                .data(chapterCrudPlugin.getChaptersByComicId(comicId))
                .build());
    }
}
