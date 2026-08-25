package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.BroadcastService;
import com.sep.comiverse.service.NotificationPreferenceService;

import com.sep.comiverse.dto.request.BroadcastRequest;
import com.sep.comiverse.dto.response.BroadcastAudiencePreviewResponse;
import com.sep.comiverse.dto.response.BroadcastResponse;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ProjectTeamMemberEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BroadcastAudienceType;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.push.NotificationPushBatchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private IProjectTeamRepository projectTeamRepository;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new BroadcastService(
                notificationRepository,
                userRepository,
                projectTeamRepository,
                preferenceService,
                eventPublisher
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesAndQueuesDeliveryOnlyForUsersWhoOptedIn() {
        UserEntity enabledReader = activeUser("READER");
        UserEntity disabledReader = activeUser("READER");
        when(userRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(enabledReader, disabledReader));
        when(preferenceService.filterEnabled(
                any(),
                eq(NotificationPreferenceKey.SYSTEM_BROADCASTS)
        )).thenReturn(List.of(enabledReader));
        when(notificationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BroadcastResponse response = broadcastService.sendBroadcast(request());

        assertEquals(1, response.getRecipientCount());
        ArgumentCaptor<Iterable<NotificationEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<NotificationEntity> saved = (List<NotificationEntity>) captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(enabledReader.getId(), saved.getFirst().getUser().getId());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationPushBatchEvent event = (NotificationPushBatchEvent) eventCaptor.getValue();
        assertEquals(1, event.deliveries().size());
        assertEquals(enabledReader.getId(), event.deliveries().getFirst().userId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsSendWhenEveryRecipientOptedOut() {
        UserEntity disabledReader = activeUser("READER");
        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(disabledReader));
        when(preferenceService.filterEnabled(
                any(),
                eq(NotificationPreferenceKey.SYSTEM_BROADCASTS)
        )).thenReturn(List.of());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> broadcastService.sendBroadcast(request())
        );

        assertEquals("No selected recipient currently allows system broadcasts.", exception.getMessage());
        verify(notificationRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewsSpecificUsersAndCountsNotificationPreferences() {
        UserEntity enabledUser = activeUser("AUTHOR");
        UserEntity optedOutUser = activeUser("TRANSLATOR");
        when(userRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(enabledUser, optedOutUser));
        when(preferenceService.filterEnabled(
                any(),
                eq(NotificationPreferenceKey.SYSTEM_BROADCASTS)
        )).thenReturn(List.of(enabledUser));

        BroadcastRequest request = request();
        request.setAudienceType(BroadcastAudienceType.USERS);
        request.setTargetRoles(null);
        request.setTargetUserIds(List.of(enabledUser.getId(), optedOutUser.getId()));

        BroadcastAudiencePreviewResponse preview = broadcastService.previewAudience(request);

        assertEquals(BroadcastAudienceType.USERS, preview.getAudienceType());
        assertEquals("2 SPECIFIC USERS", preview.getAudienceLabel());
        assertEquals(2, preview.getMatchedRecipientCount());
        assertEquals(1, preview.getEnabledRecipientCount());
        assertEquals(1, preview.getOptedOutCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsToProjectLeaderAndMembersOnlyOnce() {
        UserEntity leader = activeUser("PROJECT_LEADER");
        UserEntity translator = activeUser("TRANSLATOR");
        ProjectTeamEntity team = ProjectTeamEntity.builder()
                .title("Moonlight Translation")
                .leaderId(leader.getId())
                .members(List.of(
                        ProjectTeamMemberEntity.builder().user(leader).build(),
                        ProjectTeamMemberEntity.builder().user(translator).build()
                ))
                .build();
        team.setId(UUID.randomUUID());
        team.setDeleted(false);

        when(projectTeamRepository.findAllWithMembersByIdIn(List.of(team.getId())))
                .thenReturn(List.of(team));
        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(leader, translator));
        when(preferenceService.filterEnabled(
                any(),
                eq(NotificationPreferenceKey.SYSTEM_BROADCASTS)
        )).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BroadcastRequest request = request();
        request.setAudienceType(BroadcastAudienceType.PROJECT_TEAMS);
        request.setTargetRoles(null);
        request.setTargetTeamIds(List.of(team.getId()));

        BroadcastResponse response = broadcastService.sendBroadcast(request);

        assertEquals(2, response.getRecipientCount());
        assertEquals("PROJECT TEAM: Moonlight Translation", response.getAudienceLabel());
        ArgumentCaptor<Iterable<NotificationEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<NotificationEntity> saved = (List<NotificationEntity>) captor.getValue();
        assertEquals(2, saved.stream().map(item -> item.getUser().getId()).distinct().count());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationPushBatchEvent event = (NotificationPushBatchEvent) eventCaptor.getValue();
        assertEquals(2, event.deliveries().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsSpecificUserSelectionWhenAnAccountIsNoLongerActive() {
        UserEntity activeUser = activeUser("READER");
        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(activeUser));

        BroadcastRequest request = request();
        request.setAudienceType(BroadcastAudienceType.USERS);
        request.setTargetRoles(null);
        request.setTargetUserIds(List.of(activeUser.getId(), UUID.randomUUID()));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> broadcastService.previewAudience(request)
        );

        assertEquals(
                "One or more selected users are no longer active. Refresh the selection and try again.",
                exception.getMessage()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void allAudienceCreatesOnePrivateDeliveryForEverySupportedRole() {
        List<UserEntity> users = List.of(
                activeUser("ADMIN"),
                activeUser("MODERATOR"),
                activeUser("AUTHOR"),
                activeUser("PROJECT_LEADER"),
                activeUser("TRANSLATOR"),
                activeUser("READER")
        );
        when(userRepository.findAll(any(Specification.class))).thenReturn(users);
        when(preferenceService.filterEnabled(
                any(),
                eq(NotificationPreferenceKey.SYSTEM_BROADCASTS)
        )).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BroadcastResponse response = broadcastService.sendBroadcast(request());

        assertEquals(users.size(), response.getRecipientCount());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationPushBatchEvent event = (NotificationPushBatchEvent) eventCaptor.getValue();
        assertEquals(
                users.stream().map(UserEntity::getId).collect(java.util.stream.Collectors.toSet()),
                event.deliveries().stream()
                        .map(delivery -> delivery.userId())
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(users.size(), event.deliveries().size());
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
