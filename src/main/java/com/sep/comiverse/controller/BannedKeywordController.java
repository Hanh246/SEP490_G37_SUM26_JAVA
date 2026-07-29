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

import com.sep.comiverse.service.ChatService;

@RestController
@RequestMapping("/chat/banned-keywords")
public class BannedKeywordController {

    private final BannedKeywordCrudPlugin crudPlugin;
    private final ChatService chatService;

    @Autowired
    public BannedKeywordController(BannedKeywordCrudPlugin crudPlugin, ChatService chatService) {
        this.crudPlugin = crudPlugin;
        this.chatService = chatService;
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

    @PostMapping("/validate")
    public ResponseEntity<BaseResponse<com.sep.comiverse.dto.response.BannedKeywordValidationResponseDTO>> validate(@RequestBody com.sep.comiverse.dto.request.BannedKeywordValidationRequestDTO request) {
        com.sep.comiverse.dto.response.BannedKeywordValidationResponseDTO result = chatService.validateMessageContent(request.getContent());
        return ResponseEntity.ok(BaseResponse.<com.sep.comiverse.dto.response.BannedKeywordValidationResponseDTO>builder()
                .success(true)
                .data(result)
                .build());
    }
}
