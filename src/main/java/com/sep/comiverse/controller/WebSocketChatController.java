package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.MessageRequestDTO;
import com.sep.comiverse.dto.response.MessageResponseDTO;
import com.sep.comiverse.entity.enums.ChatType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void processChatMessage(@Payload MessageRequestDTO request, Principal principal) {
        if (principal == null || !(principal instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new CustomException(401, "UNAUTHORIZED_WEBSOCKET_MESSAGE", HttpStatus.UNAUTHORIZED);
        }

        MessageResponseDTO savedMessage = chatService.saveMessage(userPrincipal.getId(), request);

        if (ChatType.GLOBAL.equals(request.getChatType())) {
            messagingTemplate.convertAndSend("/topic/chat/global", savedMessage);
        } else if (ChatType.GROUP.equals(request.getChatType())) {
            messagingTemplate.convertAndSend("/topic/chat/group/" + request.getGroupId(), savedMessage);
        }
    }
}
