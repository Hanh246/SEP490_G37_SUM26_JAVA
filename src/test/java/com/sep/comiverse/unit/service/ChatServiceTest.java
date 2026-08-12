package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.dto.request.MessageRequestDTO;
import com.sep.comiverse.entity.BannedKeywordEntity;
import com.sep.comiverse.entity.MessageEntity;
import com.sep.comiverse.entity.enums.ChatType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IBannedKeywordRepository;
import com.sep.comiverse.repository.IMessageRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.service.ChatService;
import com.sep.comiverse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private IMessageRepository messageRepository;
    @Mock private IProjectTeamRepository projectTeamRepository;
    @Mock private UserService userService;
    @Mock private IBannedKeywordRepository bannedKeywordRepository;
    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(messageRepository, projectTeamRepository, userService, bannedKeywordRepository);
        lenient().when(messageRepository.save(any(MessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validateMessageContent_emptyContentIsAllowedWithoutRepositoryLookup() {
        assertFalse(service.validateMessageContent("   ").isBanned());
        assertFalse(service.validateMessageContent(null).isBanned());
        verifyNoInteractions(bannedKeywordRepository);
    }

    @Test
    void validateMessageContent_matchesCaseInsensitiveSubstringAndReturnsMetadata() {
        when(bannedKeywordRepository.findAll()).thenReturn(List.of(
                BannedKeywordEntity.builder().word("spoiler").category("CONTENT").severity("HIGH").build()
        ));

        var result = service.validateMessageContent("This has SPOILER inside");

        assertTrue(result.isBanned());
        assertEquals("spoiler", result.getMatchedWord());
        assertEquals("CONTENT", result.getCategory());
        assertEquals("HIGH", result.getSeverity());
    }

    @Test
    void saveMessage_globalTrimsContentAndMapsSenderSnapshot() {
        UUID senderId = UUID.randomUUID();
        MessageRequestDTO request = MessageRequestDTO.builder()
                .chatType(ChatType.GLOBAL)
                .content("  hello  ")
                .build();
        when(bannedKeywordRepository.findAll()).thenReturn(List.of());
        when(userService.findUserById(senderId))
                .thenReturn(new UserSnapshot(senderId, "Reader", "avatar.png"));

        var response = service.saveMessage(senderId, request);

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(captor.capture());
        assertEquals("hello", captor.getValue().getContent());
        assertEquals(ChatType.GLOBAL, captor.getValue().getChatType());
        assertEquals("Reader", response.getSenderName());
    }

    @Test
    void saveMessage_rejectsBlankBannedAndInvalidGroupMembership() {
        UUID senderId = UUID.randomUUID();
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.saveMessage(senderId, MessageRequestDTO.builder()
                        .chatType(ChatType.GLOBAL).content("   ").build())).getCode());

        when(bannedKeywordRepository.findAll()).thenReturn(List.of(
                BannedKeywordEntity.builder().word("badword").build()
        ));
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.saveMessage(senderId, MessageRequestDTO.builder()
                        .chatType(ChatType.GLOBAL).content("badword here").build())).getCode());

        when(bannedKeywordRepository.findAll()).thenReturn(List.of());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.saveMessage(senderId, MessageRequestDTO.builder()
                        .chatType(ChatType.GROUP).content("hello").build())).getCode());

        UUID groupId = UUID.randomUUID();
        when(projectTeamRepository.isUserMemberOfTeam(groupId, senderId)).thenReturn(false);
        assertEquals(403, assertThrows(CustomException.class, () ->
                service.saveMessage(senderId, MessageRequestDTO.builder()
                        .chatType(ChatType.GROUP).groupId(groupId).content("hello").build())).getCode());
        verify(messageRepository, never()).save(argThat(m -> m != null && ChatType.GROUP.equals(m.getChatType())));
    }

    @Test
    void getMessages_clampsPageAndSizeAndAuthorizesGroup() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MessageEntity message = MessageEntity.builder()
                .senderId(userId).chatType(ChatType.GROUP).groupId(groupId).content("x").build();
        when(projectTeamRepository.isUserMemberOfTeam(groupId, userId)).thenReturn(true);
        when(messageRepository.findByChatTypeAndGroupId(eq(ChatType.GROUP), eq(groupId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(userService.findUserById(userId)).thenReturn(new UserSnapshot(userId, "Reader", null));

        var page = service.getMessages(userId, ChatType.GROUP, groupId, 0, 999);

        assertEquals(1, page.getTotalElements());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findByChatTypeAndGroupId(eq(ChatType.GROUP), eq(groupId), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getMessages_rejectsMissingGroupIdAndNonMember() {
        UUID userId = UUID.randomUUID();
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.getMessages(userId, ChatType.GROUP, null, 1, 10)).getCode());

        UUID groupId = UUID.randomUUID();
        when(projectTeamRepository.isUserMemberOfTeam(groupId, userId)).thenReturn(false);
        assertEquals(403, assertThrows(CustomException.class, () ->
                service.getMessages(userId, ChatType.GROUP, groupId, 1, 10)).getCode());
    }

    @Test
    void deleteMessage_onlySenderCanDelete() {
        UUID senderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageEntity entity = MessageEntity.builder().senderId(senderId).build();
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(entity));

        service.deleteMessage(messageId, senderId);
        verify(messageRepository).deleteById(messageId);

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(entity));
        assertEquals(403, assertThrows(CustomException.class, () ->
                service.deleteMessage(messageId, UUID.randomUUID())).getCode());
    }

    @Test
    void deleteMessage_missingMessageReturns404() {
        UUID id = UUID.randomUUID();
        when(messageRepository.findById(id)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(CustomException.class, () ->
                service.deleteMessage(id, UUID.randomUUID())).getCode());
    }
}
