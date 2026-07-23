package com.sep.comiverse.service;

import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.dto.request.MessageRequestDTO;
import com.sep.comiverse.dto.response.MessageResponseDTO;
import com.sep.comiverse.entity.MessageEntity;
import com.sep.comiverse.entity.enums.ChatType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IMessageRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final IMessageRepository messageRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final UserService userService;

    @Transactional
    public MessageResponseDTO saveMessage(UUID senderId, MessageRequestDTO request) {

        if (ChatType.GROUP.equals(request.getChatType())) {
            if (request.getGroupId() == null) {
                throw new CustomException(400, "groupId is required for GROUP chat type", HttpStatus.BAD_REQUEST);
            }
            boolean isMember = projectTeamRepository.isUserMemberOfTeam(request.getGroupId(), senderId);
            if (!isMember) {
                throw new CustomException(403, "You are not a member of this translation team", HttpStatus.FORBIDDEN);
            }
        }

        MessageEntity messageEntity = MessageEntity.builder()
                .senderId(senderId)
                .chatType(request.getChatType())
                .groupId(request.getGroupId())
                .content(request.getContent().trim())
                .status("ACTIVE")
                .build();

        MessageEntity saved = messageRepository.save(messageEntity);
        return toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponseDTO> getMessages(UUID currentUserId, ChatType chatType, UUID groupId, int page, int size) {
        int pageIndex = page >= 1 ? page - 1 : 0;
        int pageSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize);

        Page<MessageEntity> entityPage;

        if (ChatType.GROUP.equals(chatType)) {
            if (groupId == null) {
                throw new CustomException(400, "groupId is required when chatType is GROUP", HttpStatus.BAD_REQUEST);
            }
            boolean isMember = projectTeamRepository.isUserMemberOfTeam(groupId, currentUserId);
            if (!isMember) {
                throw new CustomException(403, "You are not allowed to view messages from this translation team", HttpStatus.FORBIDDEN);
            }
            entityPage = messageRepository.findByChatTypeAndGroupId(chatType, groupId, pageRequest);
        } else {
            entityPage = messageRepository.findByChatType(chatType, pageRequest);
        }

        return entityPage.map(this::toResponseDTO);
    }

    public MessageResponseDTO toResponseDTO(MessageEntity entity) {
        UserSnapshot userSnapshot = getUserById(entity.getSenderId());
        return MessageResponseDTO.builder()
                .id(entity.getId())
                .senderId(entity.getSenderId())
                .senderName(userSnapshot.getUserName())
                .senderAvatar(userSnapshot.getAvatarURL())
                .chatType(entity.getChatType())
                .groupId(entity.getGroupId())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private UserSnapshot getUserById(UUID userId){
        if (userId == null) return null;
        return userService.findUserById(userId);
    }
}
