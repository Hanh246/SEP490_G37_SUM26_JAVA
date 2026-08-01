package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.NotificationPreferenceService;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
        lenient().when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(author));
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

    @ParameterizedTest(name = "persists enabled={0}")
    @ValueSource(booleans = {true, false})
    void persistsTheExactToggleValueAndReturnsIt(boolean enabled) {
        AtomicReference<NotificationPreferenceEntity> stored = new AtomicReference<>();
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId))
                .thenAnswer(invocation -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(preferenceRepository.save(any(NotificationPreferenceEntity.class)))
                .thenAnswer(invocation -> {
                    NotificationPreferenceEntity preference = invocation.getArgument(0);
                    stored.set(preference);
                    return preference;
                });

        NotificationPreferencesResponse response = service.updatePreferences(
                userId,
                Map.of("SUBMISSION_STATUS", enabled)
        );

        assertNotNull(stored.get());
        assertEquals(enabled, stored.get().getEnabled());
        assertEquals(enabled, response.getPreferences().get("SUBMISSION_STATUS"));
    }

    @Test
    void rejectsPreferencesThatDoNotBelongToTheUsersRole() {
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());

        assertThrows(CustomException.class, () ->
                service.updatePreferences(userId, Map.of("REVIEW_QUEUE", false))
        );
    }

    @Test
    void rejectsMissingToggleValues() {
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());
        Map<String, Boolean> request = new java.util.HashMap<>();
        request.put("SUBMISSION_STATUS", null);

        assertThrows(CustomException.class, () -> service.updatePreferences(userId, request));
    }

    @ParameterizedTest(name = "{0} exposes {1}")
    @MethodSource("rolePreferenceMatrix")
    void exposesOnlyPreferencesSupportedByEachRole(String roleName, Set<String> expectedKeys) {
        author.getRole().setRoleName(roleName);
        when(preferenceRepository.findByUser_IdAndDeletedFalse(userId)).thenReturn(List.of());

        NotificationPreferencesResponse response = service.getPreferences(userId);

        assertEquals(expectedKeys, Set.copyOf(response.getAvailableKeys()));
        assertEquals(expectedKeys, response.getPreferences().keySet());
        assertTrue(response.getPreferences().values().stream().allMatch(Boolean.TRUE::equals));
    }

    @ParameterizedTest(name = "{0} / {1} / enabled={2}")
    @MethodSource("roleDeliveryMatrix")
    void returnsThePersistedToggleForEveryRoleAndCategory(
            String roleName,
            NotificationPreferenceKey key,
            boolean enabled
    ) {
        author.getRole().setRoleName(roleName);
        NotificationPreferenceEntity preference = NotificationPreferenceEntity.builder()
                .user(author)
                .preferenceKey(key)
                .enabled(enabled)
                .build();
        preference.setDeleted(false);
        when(preferenceRepository.findByUser_IdAndPreferenceKeyAndDeletedFalse(userId, key))
                .thenReturn(Optional.of(preference));

        assertEquals(enabled, service.isEnabled(author, key));
    }

    @Test
    void deniesNullAndRoleIncompatibleCategoriesByDefault() {
        author.getRole().setRoleName("AUTHOR");

        assertFalse(service.isEnabled(author, null));
        assertFalse(service.isEnabled(author, NotificationPreferenceKey.REVIEW_QUEUE));
        assertFalse(service.isEnabled(null, NotificationPreferenceKey.SUBMISSION_STATUS));
    }

    private static Stream<Arguments> rolePreferenceMatrix() {
        Set<String> common = Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY");
        return Stream.of(
                Arguments.of("READER", common),
                Arguments.of("ADMIN", common),
                Arguments.of("MODERATOR", Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY", "REVIEW_QUEUE")),
                Arguments.of("STAFF", Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY", "REVIEW_QUEUE")),
                Arguments.of("AUTHOR", Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY", "SUBMISSION_STATUS")),
                Arguments.of("TRANSLATOR", Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY", "PROJECT_OPPORTUNITIES", "TEAM_UPDATES")),
                Arguments.of("PROJECT_LEADER", Set.of("SYSTEM_BROADCASTS", "COMMENT_REPLIES", "FORUM_ACTIVITY", "PROJECT_OPPORTUNITIES", "TEAM_UPDATES", "TEAM_JOIN_REQUESTS"))
        );
    }

    private static Stream<Arguments> roleDeliveryMatrix() {
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
