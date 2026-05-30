package com.sep.comiverse.common.controller;

import com.sep.comiverse.common.dto.pagination.PaginationMetadata;
import com.sep.comiverse.common.dto.pagination.PaginationResponse;
import com.sep.comiverse.common.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.common.dto.response.BaseResponse;
import com.sep.comiverse.common.entity.BaseEntity;
import com.sep.comiverse.common.plugin.AbstractCrudPlugin;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Data
@RequiredArgsConstructor
public abstract class BaseController<MODEL extends BaseEntity, DTO, ID, P extends PaginationSearchDTO> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected final AbstractCrudPlugin<MODEL, DTO, ID, P> crudPlugin;
    private final Class<MODEL> modelClass;

    // TODO remove mapping on CRUD API for selective API exposure
    @PostMapping
    public ResponseEntity<BaseResponse<DTO>> create(@Valid @RequestBody DTO dto) {
        DTO created = crudPlugin.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<DTO>builder().success(true).data(created).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<DTO>> findById(
            @PathVariable
            @Parameter(required = true)
            ID id) {
        var data = crudPlugin.read(id);
        return data.<ResponseEntity<BaseResponse<DTO>>>map(dto -> ResponseEntity.ok(BaseResponse.<DTO>builder()
                .success(true)
                .data(dto)
                .build())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.<DTO>builder().success(false).build()));
    }

    @GetMapping
    public ResponseEntity<PaginationResponse<List<DTO>>> findAll(@Valid @ParameterObject P paginationDTO) {
        var data = crudPlugin.list(paginationDTO);
        return ResponseEntity
                .ok(PaginationResponse.<List<DTO>>builder()
                        .metadata(
                                new PaginationMetadata(
                                        paginationDTO.getPage(),
                                        paginationDTO.getSize(),
                                        data.getTotalElements(),
                                        data.getTotalPages()
                                )
                        )
                        .success(true)
                        .data(data.toList())
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<DTO>> update(@PathVariable ID id, @RequestBody DTO dto) {
        DTO updated = crudPlugin.update(id, dto);
        return ResponseEntity.ok(BaseResponse.<DTO>builder()
                .success(true)
                .data(updated)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable ID id) {
        try {
            crudPlugin.delete(id);
            return ResponseEntity.ok(BaseResponse.<Void>builder().success(true).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.<Void>builder().success(false).build());
        }
    }
}
