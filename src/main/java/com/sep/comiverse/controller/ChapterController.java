package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/chapters")
public class ChapterController extends BaseController<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final ChapterCrudPlugin chapterCrudPlugin;
    @Autowired
    public ChapterController(ChapterCrudPlugin crud) {
        super(crud, ChapterEntity.class);
        this.chapterCrudPlugin = crud;
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "get chapter detail")
    public ResponseEntity<BaseResponse<ChapterDTO>> getChapterDetail(
            @PathVariable UUID id,
            HttpServletRequest request) {

        // Extract IP Address if userId is absent
        String clientIp = request.getRemoteAddr();
        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                        .success(true)
                        .data(chapterCrudPlugin.getChapterDetail(id, clientIp))
                        .build());
    }
}
