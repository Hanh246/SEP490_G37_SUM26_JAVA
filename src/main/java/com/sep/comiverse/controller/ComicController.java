package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comics")
public class ComicController extends BaseController<ComicEntity, ComicDTO, UUID, PaginationSearchDTO> {

    private final ComicCrudPlugin comicCrudPlugin;

    @Autowired
    public ComicController(ComicCrudPlugin crud) {
        super(crud, ComicEntity.class);
        this.comicCrudPlugin = crud;
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ComicDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ComicDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @GetMapping("/explore")
    @Operation(summary = "Explore catalog using highly optimized cursor pagination with filters and dynamic sorting")
    public ResponseEntity<BaseResponse<CursorResponseDTO<ComicDTO>>> getExploreComics(
            @Valid @ParameterObject ComicExploreRequestDTO request) {

        CursorResponseDTO<ComicDTO> result = comicCrudPlugin.getExploreComicsCursor(request);

        return ResponseEntity.ok(BaseResponse.<CursorResponseDTO<ComicDTO>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @Override
    @GetMapping("/{id}")
    @Operation(summary = "get comic detail")
    public ResponseEntity<BaseResponse<ComicDTO>> findById(
            @PathVariable
            @Parameter(required = true)
            UUID id) {
        var data = comicCrudPlugin.getComicDetail(id);
        return ResponseEntity.ok(BaseResponse.<ComicDTO>builder()
                .success(true)
                .data(data)
                .build());
    }
}
