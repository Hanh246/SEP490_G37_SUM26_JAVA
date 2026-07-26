package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.NotificationPreferencesResponse;
import com.sep.comiverse.entity.NotificationPreferenceEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationPreferenceRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final Set<NotificationPreferenceKey> COMMON_KEYS = EnumSet.of(
            NotificationPreferenceKey.SYSTEM_BROADCASTS,
            NotificationPreferenceKey.FORUM_ACTIVITY
    );

    private final INotificationPreferenceRepository preferenceRepository;
    private final IUserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        UserEntity user = findUser(userId);
        Set<NotificationPreferenceKey> availableKeys = availableKeys(user);
        Map<NotificationPreferenceKey, Boolean> saved = loadSavedPreferences(userId);

        Map<String, Boolean> preferences = new LinkedHashMap<>();
        for (NotificationPreferenceKey key : availableKeys) {
            preferences.put(key.name(), saved.getOrDefault(key, true));
        }

        return NotificationPreferencesResponse.builder()
                .role(normalizeRole(user))
                .availableKeys(availableKeys.stream().map(Enum::name).toList())
                .preferences(preferences)
                .build();
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, Map<String, Boolean> requestedPreferences) {
        UserEntity user = findUser(userId);
        Set<NotificationPreferenceKey> availableKeys = availableKeys(user);
        Map<NotificationPreferenceKey, NotificationPreferenceEntity> existing = new EnumMap<>(NotificationPreferenceKey.class);
        preferenceRepository.findByUser_IdAndDeletedFalse(userId)
                .forEach(preference -> existing.put(preference.getPreferenceKey(), preference));

        for (Map.Entry<String, Boolean> entry : requestedPreferences.entrySet()) {
            NotificationPreferenceKey key = parseKey(entry.getKey());
            if (!availableKeys.contains(key)) {
                throw new CustomException(400, "Notification preference is not available for this role: " + key, HttpStatus.BAD_REQUEST);
            }
            if (entry.getValue() == null) {
                throw new CustomException(400, "Notification preference value is required: " + key, HttpStatus.BAD_REQUEST);
            }

            NotificationPreferenceEntity preference = existing.getOrDefault(
                    key,
                    NotificationPreferenceEntity.builder()
                            .user(user)
                            .preferenceKey(key)
                            .build()
            );
            preference.setEnabled(entry.getValue());
            preferenceRepository.save(preference);
        }

        return getPreferences(userId);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UserEntity user, NotificationPreferenceKey key) {
        if (user == null || key == null || !availableKeys(user).contains(key)) {
            return false;
        }
        return preferenceRepository.findByUser_IdAndPreferenceKeyAndDeletedFalse(user.getId(), key)
                .map(NotificationPreferenceEntity::getEnabled)
                .orElse(true);
    }

    private Map<NotificationPreferenceKey, Boolean> loadSavedPreferences(UUID userId) {
        Map<NotificationPreferenceKey, Boolean> saved = new EnumMap<>(NotificationPreferenceKey.class);
        preferenceRepository.findByUser_IdAndDeletedFalse(userId)
                .forEach(preference -> saved.put(preference.getPreferenceKey(), preference.getEnabled()));
        return saved;
    }

    private Set<NotificationPreferenceKey> availableKeys(UserEntity user) {
        LinkedHashSet<NotificationPreferenceKey> keys = new LinkedHashSet<>(COMMON_KEYS);
        switch (normalizeRole(user)) {
            case "MODERATOR", "STAFF" -> keys.add(NotificationPreferenceKey.REVIEW_QUEUE);
            case "AUTHOR" -> keys.add(NotificationPreferenceKey.SUBMISSION_STATUS);
            case "TRANSLATOR" -> {
                keys.add(NotificationPreferenceKey.PROJECT_OPPORTUNITIES);
                keys.add(NotificationPreferenceKey.TEAM_UPDATES);
            }
            case "PROJECT_LEADER" -> {
                keys.add(NotificationPreferenceKey.PROJECT_OPPORTUNITIES);
                keys.add(NotificationPreferenceKey.TEAM_UPDATES);
                keys.add(NotificationPreferenceKey.TEAM_JOIN_REQUESTS);
            }
            default -> {
                // Common notifications are available to readers and administrators.
            }
        }
        return keys;
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
    }

    private String normalizeRole(UserEntity user) {
        if (user.getRole() == null || user.getRole().getRoleName() == null) {
            return "READER";
        }
        return user.getRole().getRoleName().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private NotificationPreferenceKey parseKey(String rawKey) {
        try {
            return NotificationPreferenceKey.valueOf(rawKey == null ? "" : rawKey.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(400, "Unknown notification preference: " + rawKey, HttpStatus.BAD_REQUEST);
        }
    }
}
