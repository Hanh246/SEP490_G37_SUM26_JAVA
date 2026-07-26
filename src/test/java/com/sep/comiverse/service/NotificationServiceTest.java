package com.sep.comiverse.service;

import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private NotificationPreferenceService preferenceService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                messagingTemplate,
                preferenceService
        );
    }

    @Test
    void disabledPreferencePreventsDatabaseAndWebSocketNotification() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .email("reader@example.com")
                .status("ACTIVE")
                .build();
        user.setId(userId);
        user.setDeleted(false);

        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(preferenceService.isEnabled(user, NotificationPreferenceKey.FORUM_ACTIVITY)).thenReturn(false);

        boolean sent = notificationService.notifyUser(
                userId,
                "New forum reply",
                "Someone replied to your comment.",
                "FORUM",
                "/forum/thread/1",
                NotificationPreferenceKey.FORUM_ACTIVITY
        );

        assertFalse(sent);
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(messagingTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class)
        );
    }
}
