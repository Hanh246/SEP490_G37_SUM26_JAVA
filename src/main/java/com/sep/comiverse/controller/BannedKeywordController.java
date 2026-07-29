package com.sep.comiverse.controller;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.BannedKeywordEntity;
import com.sep.comiverse.plugin.crud.BannedKeywordCrudPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat/banned-keywords")
public class BannedKeywordController {

    private final BannedKeywordCrudPlugin crudPlugin;

    @Autowired
    public BannedKeywordController(BannedKeywordCrudPlugin crudPlugin) {
        this.crudPlugin = crudPlugin;
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<BannedKeywordDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<BannedKeywordDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @PostMapping
    public ResponseEntity<BaseResponse<BannedKeywordDTO>> create(@RequestBody BannedKeywordDTO dto) {
        return ResponseEntity.ok(BaseResponse.<BannedKeywordDTO>builder()
                .success(true)
                .data(crudPlugin.create(dto))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        crudPlugin.delete(id);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .build());
    }
}
