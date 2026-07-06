package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chapters")
public class ChapterController extends BaseController<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final ChapterCrudPlugin chapterCrudPlugin;
    private final JwtTokenUtil jwtTokenUtil;

    @Autowired
    public ChapterController(ChapterCrudPlugin crud, JwtTokenUtil jwtTokenUtil) {
        super(crud, ChapterEntity.class);
        this.chapterCrudPlugin = crud;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "get chapter detail")
    public ResponseEntity<BaseResponse<ChapterDTO>> getChapterDetail(
            @PathVariable UUID id,
            HttpServletRequest request) {

        UUID userId = jwtTokenUtil.getCurrentUserId();
        String clientIp = request.getRemoteAddr();
        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                        .success(true)
                        .data(chapterCrudPlugin.getChapterDetail(id, userId, clientIp))
                        .build());
    }
}
