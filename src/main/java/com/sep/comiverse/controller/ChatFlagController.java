package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChatFlagDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChatFlagEntity;
import com.sep.comiverse.plugin.crud.ChatFlagCrudPlugin;
import com.sep.comiverse.repository.IChatFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat-flags")
public class ChatFlagController extends BaseController<ChatFlagEntity, ChatFlagDTO, UUID, PaginationSearchDTO> {

    @Autowired
    private IChatFlagRepository chatFlagRepository;

    @Autowired
    private com.sep.comiverse.service.AuditLogService auditLogService;

    @Autowired
    public ChatFlagController(ChatFlagCrudPlugin crud) {
        super(crud, ChatFlagEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ChatFlagDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ChatFlagDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @PutMapping("/{id}/warn")
    @Transactional
    public ResponseEntity<BaseResponse<ChatFlagDTO>> warn(@PathVariable UUID id) {
        ChatFlagEntity flag = chatFlagRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chat flag with id " + id + " not found"));

        flag.setStatus("warned");
        ChatFlagEntity saved = chatFlagRepository.save(flag);
        auditLogService.log("CHAT_MODERATION", "Warned user for flagged chat message: \"" + flag.getMessage() + "\"");

        return ResponseEntity.ok(BaseResponse.<ChatFlagDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(saved))
                .build());
    }

    @Override
    @Transactional
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        String msg = "Unknown Message";
        try {
            var existing = chatFlagRepository.findById(id).orElse(null);
            if (existing != null) {
                msg = existing.getMessage();
            }
        } catch (Exception e) {}
        
        auditLogService.log("CHAT_MODERATION", "Resolved/ignored chat flag for message: \"" + msg + "\"");
        return super.delete(id);
    }
}
