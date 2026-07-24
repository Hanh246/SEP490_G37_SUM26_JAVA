package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.request.MessageRequestDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.MessageResponseDTO;
import com.sep.comiverse.entity.enums.ChatType;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat/messages")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "APIs for Global and Group chat messages")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    @Operation(summary = "Send chat message via REST", description = "Save chat message to database and broadcast to WebSocket subscribers")
    public ResponseEntity<BaseResponse<MessageResponseDTO>> sendMessage(
            @Valid @RequestBody MessageRequestDTO request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MessageResponseDTO savedMessage = chatService.saveMessage(principal.getId(), request);

        // Broadcast event to WebSocket channel
        if (ChatType.GLOBAL.equals(request.getChatType())) {
            messagingTemplate.convertAndSend("/topic/chat/global", savedMessage);
        } else if (ChatType.GROUP.equals(request.getChatType())) {
            messagingTemplate.convertAndSend("/topic/chat/group/" + request.getGroupId(), savedMessage);
        }

        return ResponseEntity.ok(
                BaseResponse.<MessageResponseDTO>builder()
                        .success(true)
                        .data(savedMessage)
                        .build()
        );
    }

    @GetMapping
    @Operation(summary = "Get paginated chat messages", description = "Retrieve latest chat messages for Global room or Translation Group room")
    public ResponseEntity<PaginationResponse<List<MessageResponseDTO>>> getMessages(
            @RequestParam(name = "chat_type", defaultValue = "GLOBAL") ChatType chatType,
            @RequestParam(name = "group_id", required = false) UUID groupId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<MessageResponseDTO> resultPage = chatService.getMessages(principal.getId(), chatType, groupId, page, limit);

        PaginationMetadata metadata = new PaginationMetadata(
                page,
                limit,
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );

        return ResponseEntity.ok(
                PaginationResponse.<List<MessageResponseDTO>>builder()
                        .success(true)
                        .metadata(metadata)
                        .data(resultPage.getContent())
                        .build()
        );
    }
}
