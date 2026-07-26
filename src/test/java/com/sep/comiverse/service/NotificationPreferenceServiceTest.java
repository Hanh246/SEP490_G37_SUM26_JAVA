package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.NotificationPreferencesResponse;
import com.sep.comiverse.entity.NotificationPreferenceEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationPreferenceRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private INotificationPreferenceRepository preferenceRepository;
    @Mock
    private IUserRepository userRepository;

    private NotificationPreferenceService service;
    private UUID userId;
    private UserEntity author;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferenceRepository, userRepository);
        userId = UUID.randomUUID();
        RoleEntity role = new RoleEntity();
        role.setRoleName("AUTHOR");
        author = UserEntity.builder().role(role).email("author@example.com").build();
        author.setId(userId);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(author));
    }

    @Test
    void returnsCommonAndAuthorPreferencesWithEnabledDefaults() {
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());

        NotificationPreferencesResponse response = service.getPreferences(userId);

        assertEquals("AUTHOR", response.getRole());
        assertTrue(response.getAvailableKeys().contains("SUBMISSION_STATUS"));
        assertTrue(response.getAvailableKeys().contains("SYSTEM_BROADCASTS"));
        assertTrue(response.getPreferences().get("SUBMISSION_STATUS"));
        assertFalse(response.getAvailableKeys().contains("REVIEW_QUEUE"));
    }

    @Test
    void savesAnAvailablePreferenceForTheUser() {
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());
        when(preferenceRepository.save(any(NotificationPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePreferences(userId, Map.of("SUBMISSION_STATUS", false));

        verify(preferenceRepository).save(any(NotificationPreferenceEntity.class));
    }

    @Test
    void rejectsPreferencesThatDoNotBelongToTheUsersRole() {
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());

        assertThrows(CustomException.class, () ->
                service.updatePreferences(userId, Map.of("REVIEW_QUEUE", false))
        );
    }
}
