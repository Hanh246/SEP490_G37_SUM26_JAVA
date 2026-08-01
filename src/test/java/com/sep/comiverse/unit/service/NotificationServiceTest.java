package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.NotificationPreferenceService;
import com.sep.comiverse.service.NotificationService;

import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                messagingTemplate,
                preferenceService,
                eventPublisher
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

    @ParameterizedTest(name = "{0} / {1} / enabled={2}")
    @MethodSource("directDeliveryMatrix")
    void toggleControlsDatabaseAndWebSocketDeliveryForEveryCategory(
            String roleName,
            NotificationPreferenceKey key,
            boolean enabled
    ) {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, roleName);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(preferenceService.isEnabled(user, key)).thenReturn(enabled);
        if (enabled) {
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        boolean sent = notificationService.notifyUser(
                userId,
                "Workflow update",
                "A workflow event occurred.",
                "UPDATE",
                "/target",
                key
        );

        assertEquals(enabled, sent);
        if (enabled) {
            verify(notificationRepository).save(any(NotificationEntity.class));
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/notifications/" + userId),
                    any(Object.class)
            );
            verify(eventPublisher).publishEvent(any(Object.class));
        } else {
            verify(notificationRepository, never()).save(any(NotificationEntity.class));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleDeliveryExcludesUsersWhoDisabledTheCategory() {
        UserEntity enabledModerator = activeUser(UUID.randomUUID(), "MODERATOR");
        UserEntity disabledModerator = activeUser(UUID.randomUUID(), "MODERATOR");
        when(userRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(enabledModerator, disabledModerator));
        when(preferenceService.isEnabled(enabledModerator, NotificationPreferenceKey.REVIEW_QUEUE)).thenReturn(true);
        when(preferenceService.isEnabled(disabledModerator, NotificationPreferenceKey.REVIEW_QUEUE)).thenReturn(false);
        when(notificationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int sentCount = notificationService.notifyRoles(
                List.of("MODERATOR"),
                "New review",
                "A submission is waiting.",
                "UPDATE",
                NotificationPreferenceKey.REVIEW_QUEUE
        );

        assertEquals(1, sentCount);
        verify(notificationRepository).saveAll(argThat(notifications -> {
            List<NotificationEntity> values = (List<NotificationEntity>) notifications;
            return values.size() == 1 && values.getFirst().getUser().equals(enabledModerator);
        }));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/notifications/" + enabledModerator.getId()),
                any(Object.class)
        );
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/notifications/" + disabledModerator.getId()),
                any(Object.class)
        );
    }

    @Test
    void nullCategoryCannotBypassPreferences() {
        boolean sent = notificationService.notifyUser(
                UUID.randomUUID(),
                "Uncategorized",
                "This must not be delivered.",
                "INFO",
                "/target",
                null
        );

        assertFalse(sent);
        verifyNoInteractions(userRepository, notificationRepository, messagingTemplate, preferenceService);
    }

    private static UserEntity activeUser(UUID userId, String roleName) {
        RoleEntity role = RoleEntity.builder().roleName(roleName).build();
        UserEntity user = UserEntity.builder()
                .email(roleName.toLowerCase() + "-" + userId + "@example.com")
                .role(role)
                .status("ACTIVE")
                .build();
        user.setId(userId);
        user.setDeleted(false);
        return user;
    }

    private static Stream<Arguments> directDeliveryMatrix() {
        return Stream.of(
                        new Object[]{"READER", NotificationPreferenceKey.SYSTEM_BROADCASTS},
                        new Object[]{"READER", NotificationPreferenceKey.COMMENT_REPLIES},
                        new Object[]{"READER", NotificationPreferenceKey.FORUM_ACTIVITY},
                        new Object[]{"ADMIN", NotificationPreferenceKey.SYSTEM_BROADCASTS},
                        new Object[]{"ADMIN", NotificationPreferenceKey.COMMENT_REPLIES},
                        new Object[]{"ADMIN", NotificationPreferenceKey.FORUM_ACTIVITY},
                        new Object[]{"MODERATOR", NotificationPreferenceKey.REVIEW_QUEUE},
                        new Object[]{"AUTHOR", NotificationPreferenceKey.SUBMISSION_STATUS},
                        new Object[]{"TRANSLATOR", NotificationPreferenceKey.PROJECT_OPPORTUNITIES},
                        new Object[]{"TRANSLATOR", NotificationPreferenceKey.TEAM_UPDATES},
                        new Object[]{"PROJECT_LEADER", NotificationPreferenceKey.PROJECT_OPPORTUNITIES},
                        new Object[]{"PROJECT_LEADER", NotificationPreferenceKey.TEAM_UPDATES},
                        new Object[]{"PROJECT_LEADER", NotificationPreferenceKey.TEAM_JOIN_REQUESTS}
                )
                .flatMap(pair -> Stream.of(
                        Arguments.of(pair[0], pair[1], true),
                        Arguments.of(pair[0], pair[1], false)
                ));
    }
}
