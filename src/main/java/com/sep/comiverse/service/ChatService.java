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

import java.net.URI;
import java.util.UUID;

import com.sep.comiverse.repository.IBannedKeywordRepository;
import com.sep.comiverse.entity.BannedKeywordEntity;
import java.util.List;

import com.sep.comiverse.dto.response.BannedKeywordValidationResponseDTO;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final IMessageRepository messageRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final UserService userService;
    private final IBannedKeywordRepository bannedKeywordRepository;

    @Transactional(readOnly = true)
    public BannedKeywordValidationResponseDTO validateMessageContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return BannedKeywordValidationResponseDTO.builder().isBanned(false).build();
        }

        List<BannedKeywordEntity> bannedKeywords = bannedKeywordRepository.findAll();
        String contentLower = content.toLowerCase();
        
        for (BannedKeywordEntity kw : bannedKeywords) {
            String wordLower = kw.getWord().toLowerCase();
            if (contentLower.contains(wordLower)) {
                return BannedKeywordValidationResponseDTO.builder()
                        .isBanned(true)
                        .matchedWord(kw.getWord())
                        .category(kw.getCategory())
                        .severity(kw.getSeverity())
                        .reason("Backend Filter Exact Match")
                        .build();
            }
        }
        return BannedKeywordValidationResponseDTO.builder().isBanned(false).build();
    }

    @Transactional
    public MessageResponseDTO saveMessage(UUID senderId, MessageRequestDTO request) {
        String content = request.getContent() != null ? request.getContent().trim() : "";
        String imageUrl = normalizeImageUrl(request.getImageUrl());
        if (content.isEmpty() && imageUrl == null) {
            throw new CustomException(400, "Message must contain text or an image", HttpStatus.BAD_REQUEST);
        }
        if (content.length() > 2000) {
            throw new CustomException(400, "Content length cannot exceed 2000 characters", HttpStatus.BAD_REQUEST);
        }
        if (request.getChatType() == null) {
            throw new CustomException(400, "chatType is required", HttpStatus.BAD_REQUEST);
        }

        // Backend Banned Keyword Filter
        if (!content.isEmpty()) {
            BannedKeywordValidationResponseDTO validation = validateMessageContent(content);
            if (validation.isBanned()) {
                throw new CustomException(400, "Message blocked by Server Filter! Contains banned keyword: " + validation.getMatchedWord(), HttpStatus.BAD_REQUEST);
            }
        }

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
                .content(content)
                .imageUrl(imageUrl)
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
                .imageUrl(entity.getImageUrl())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String normalizeImageUrl(String rawImageUrl) {
        if (rawImageUrl == null || rawImageUrl.isBlank()) return null;
        String imageUrl = rawImageUrl.trim();
        if (imageUrl.length() > 2048) {
            throw new CustomException(400, "Image URL length cannot exceed 2048 characters", HttpStatus.BAD_REQUEST);
        }
        try {
            URI uri = URI.create(imageUrl);
            boolean supportedScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Unsupported image URL");
            }
            return imageUrl;
        } catch (IllegalArgumentException ex) {
            throw new CustomException(400, "Image URL must be a valid HTTP or HTTPS URL", HttpStatus.BAD_REQUEST);
        }
    }

    private UserSnapshot getUserById(UUID userId){
        if (userId == null) return null;
        return userService.findUserById(userId);
    }

    @Transactional
    public void deleteMessage(UUID messageId, UUID currentUserId) {
        MessageEntity entity = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(404, "Message not found", HttpStatus.NOT_FOUND));
        
        // Ensure that the user attempting to delete the message is the sender
        if (!entity.getSenderId().equals(currentUserId)) {
            throw new CustomException(403, "You can only delete your own messages", HttpStatus.FORBIDDEN);
        }

        messageRepository.deleteById(messageId);
    }
}
