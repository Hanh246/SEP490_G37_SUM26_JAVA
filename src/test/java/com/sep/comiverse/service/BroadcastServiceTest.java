package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.BroadcastRequest;
import com.sep.comiverse.dto.response.BroadcastResponse;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private BroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new BroadcastService(
                notificationRepository,
                userRepository,
                preferenceService,
                messagingTemplate
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsDatabaseAndWebSocketNotificationOnlyToUsersWhoOptedIn() {
        UserEntity enabledReader = activeUser("READER");
        UserEntity disabledReader = activeUser("READER");
        when(userRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(enabledReader, disabledReader));
        when(preferenceService.isEnabled(enabledReader, NotificationPreferenceKey.SYSTEM_BROADCASTS)).thenReturn(true);
        when(preferenceService.isEnabled(disabledReader, NotificationPreferenceKey.SYSTEM_BROADCASTS)).thenReturn(false);
        when(notificationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BroadcastResponse response = broadcastService.sendBroadcast(request());

        assertEquals(1, response.getRecipientCount());
        ArgumentCaptor<Iterable<NotificationEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<NotificationEntity> saved = (List<NotificationEntity>) captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(enabledReader.getId(), saved.getFirst().getUser().getId());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/notifications/" + enabledReader.getId()),
                any(Object.class)
        );
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/notifications/" + disabledReader.getId()),
                any(Object.class)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsNothingWhenEveryRecipientOptedOut() {
        UserEntity disabledReader = activeUser("READER");
        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(disabledReader));
        when(preferenceService.isEnabled(disabledReader, NotificationPreferenceKey.SYSTEM_BROADCASTS)).thenReturn(false);
        when(notificationRepository.saveAll(any())).thenReturn(List.of());

        BroadcastResponse response = broadcastService.sendBroadcast(request());

        assertEquals(0, response.getRecipientCount());
        verify(notificationRepository).saveAll(List.of());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    private static BroadcastRequest request() {
        BroadcastRequest request = new BroadcastRequest();
        request.setType("INFO");
        request.setTitle("System announcement");
        request.setMessage("Scheduled maintenance tonight.");
        request.setTargetRoles(List.of("ALL"));
        return request;
    }

    private static UserEntity activeUser(String roleName) {
        RoleEntity role = RoleEntity.builder().roleName(roleName).build();
        UserEntity user = UserEntity.builder()
                .email(UUID.randomUUID() + "@example.com")
                .role(role)
                .status("ACTIVE")
                .build();
        user.setId(UUID.randomUUID());
        user.setDeleted(false);
        return user;
    }
}
