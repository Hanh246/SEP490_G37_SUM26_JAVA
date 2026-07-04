package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            HttpServletRequest request,
            Authentication authentication) {

        UUID userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            userId = principal.getId();
        }

        String clientIp = request.getRemoteAddr();
        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                        .success(true)
                        .data(chapterCrudPlugin.getChapterDetail(id, userId, clientIp))
                        .build());
    }
}
