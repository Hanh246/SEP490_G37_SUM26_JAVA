package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final INotificationRepository notificationRepository;
    private final IUserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationPreferenceService notificationPreferenceService;

    @Transactional
    public boolean notifyUser(UUID userId, String title, String message, String type, NotificationPreferenceKey preferenceKey) {
        return notifyUser(userId, title, message, type, null, preferenceKey);
    }

    @Transactional
    public boolean notifyUser(
            UUID userId,
            String title,
            String message,
            String type,
            String actionUrl,
            NotificationPreferenceKey preferenceKey
    ) {
        if (userId == null || preferenceKey == null) {
            return false;
        }

        return userRepository.findByIdWithRole(userId)
                .filter(this::canReceiveNotifications)
                .filter(user -> notificationPreferenceService.isEnabled(user, preferenceKey))
                .map(user -> {
                    NotificationEntity entity = buildWorkflowNotification(user, title, message, type, null, actionUrl);
                    NotificationEntity saved = notificationRepository.save(entity);
                    NotificationResponse response = toResponse(saved);
                    messagingTemplate.convertAndSend("/topic/notifications/" + user.getId(), response);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int notifyRoles(
            Collection<String> roles,
            String title,
            String message,
            String type,
            NotificationPreferenceKey preferenceKey
    ) {
        if (roles == null || roles.isEmpty() || preferenceKey == null) {
            return 0;
        }

        List<String> normalizedRoles = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedRoles.isEmpty()) {
            return 0;
        }

        Specification<UserEntity> spec = (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))),
                cb.or(cb.isNull(root.get("status")), cb.equal(cb.upper(root.get("status")), "ACTIVE")),
                cb.upper(root.get("role").get("roleName")).in(normalizedRoles)
        );
        List<UserEntity> recipients = userRepository.findAll(spec).stream()
                .filter(user -> notificationPreferenceService.isEnabled(user, preferenceKey))
                .toList();
        String targetRoles = String.join(", ", normalizedRoles);

        List<NotificationEntity> savedList = notificationRepository.saveAll(recipients.stream()
                .map(user -> buildWorkflowNotification(user, title, message, type, targetRoles, null))
                .toList());

        for (NotificationEntity saved : savedList) {
            NotificationResponse response = toResponse(saved);
            messagingTemplate.convertAndSend("/topic/notifications/" + saved.getUser().getId(), response);
        }

        return recipients.size();
    }

    @Transactional
    public int notifyModeratorsWithLanguage(
            String language,
            String title,
            String message,
            String type,
            NotificationPreferenceKey preferenceKey
    ) {
        if (preferenceKey == null) {
            return 0;
        }

        Specification<UserEntity> spec = (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))),
                cb.or(cb.isNull(root.get("status")), cb.equal(cb.upper(root.get("status")), "ACTIVE")),
                cb.equal(cb.upper(root.get("role").get("roleName")), "MODERATOR")
        );

        List<UserEntity> allModerators = userRepository.findAll(spec);
        List<UserEntity> recipients = new java.util.ArrayList<>();
        
        for (UserEntity mod : allModerators) {
            if (!notificationPreferenceService.isEnabled(mod, preferenceKey)) {
                continue;
            }
            // Check language scope
            if (mod.getAssignedLanguages() == null || mod.getAssignedLanguages().isBlank()) {
                // If no scope is defined, fallback to default or assume they receive it?
                // For safety, they shouldn't receive it unless they have the language.
                // But previously they received all. To be strict:
                continue;
            }
            
            boolean hasLanguage = false;
            for (String lang : mod.getAssignedLanguages().split(",")) {
                if (lang.trim().equalsIgnoreCase(language)) {
                    hasLanguage = true;
                    break;
                }
            }
            
            if (hasLanguage) {
                recipients.add(mod);
            }
        }

        List<NotificationEntity> savedList = notificationRepository.saveAll(recipients.stream()
                .map(user -> buildWorkflowNotification(user, title, message, type, "MODERATOR", null))
                .toList());

        for (NotificationEntity saved : savedList) {
            NotificationResponse response = toResponse(saved);
            messagingTemplate.convertAndSend("/topic/notifications/" + saved.getUser().getId(), response);
        }

        return recipients.size();
    }

    public List<NotificationResponse> getNotificationsForUser(UUID userId) {
        List<NotificationEntity> notifications = notificationRepository.findByUserId(userId);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCountForUser(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(404, "Notification not found", HttpStatus.NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(403, "Access denied to notification", HttpStatus.FORBIDDEN);
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<NotificationEntity> unread = notificationRepository.findByUserId(userId).stream()
                .filter(n -> !n.getIsRead())
                .collect(Collectors.toList());

        for (NotificationEntity n : unread) {
            n.setIsRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .actionUrl(entity.getActionUrl())
                .isRead(entity.getIsRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private boolean canReceiveNotifications(UserEntity user) {
        return user != null
                && !Boolean.TRUE.equals(user.getDeleted())
                && (user.getStatus() == null || "ACTIVE".equalsIgnoreCase(user.getStatus()));
    }

    private NotificationEntity buildWorkflowNotification(
            UserEntity user,
            String title,
            String message,
            String type,
            String targetRoles,
            String actionUrl
    ) {
        return NotificationEntity.builder()
                .user(user)
                .title(title == null ? "ComiVerse update" : title.trim())
                .message(message == null ? "You have a new workflow update." : message.trim())
                .type(type == null ? "INFO" : type.trim().toUpperCase(Locale.ROOT))
                .targetRoles(targetRoles)
                .actionUrl(actionUrl == null || actionUrl.isBlank() ? null : actionUrl.trim())
                .isRead(false)
                .build();
    }
}
