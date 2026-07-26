package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.BroadcastRequest;
import com.sep.comiverse.dto.response.BroadcastResponse;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.NotificationEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final INotificationRepository notificationRepository;
    private final IUserRepository userRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send a broadcast announcement to all users matching the target roles.
     */
    @Transactional
    public BroadcastResponse sendBroadcast(BroadcastRequest request) {
        List<String> targetRoles = request.getTargetRoles().stream()
                .map(r -> r.toUpperCase().trim())
                .collect(Collectors.toList());

        boolean isAll = targetRoles.contains("ALL");

        // Build specification to find active, non-deleted users
        Specification<UserEntity> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("deleted"), false),
                        cb.equal(root.get("status"), "ACTIVE")
                );

        // If not "ALL", also filter by role names
        if (!isAll) {
            Specification<UserEntity> roleSpec = (root, query, cb) ->
                    root.get("role").get("roleName").in(targetRoles);
            spec = spec.and(roleSpec);
        }

        List<UserEntity> recipients = userRepository.findAll(spec);

        if (recipients.isEmpty()) {
            throw new CustomException(400, "No active users found for the selected roles.", HttpStatus.BAD_REQUEST);
        }

        UUID broadcastId = UUID.randomUUID();
        String targetRolesStr = isAll ? "ALL" : String.join(", ", targetRoles);

        List<NotificationEntity> notifications = recipients.stream()
                .filter(user -> notificationPreferenceService.isEnabled(user, NotificationPreferenceKey.SYSTEM_BROADCASTS))
                .map(user -> NotificationEntity.builder()
                        .user(user)
                        .title(request.getTitle().trim())
                        .message(request.getMessage().trim())
                        .type(request.getType().toUpperCase().trim())
                        .broadcastId(broadcastId)
                        .targetRoles(targetRolesStr)
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        List<NotificationEntity> savedNotifications = notificationRepository.saveAll(notifications);
        for (NotificationEntity notification : savedNotifications) {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + notification.getUser().getId(),
                    toNotificationResponse(notification)
            );
        }

        return BroadcastResponse.builder()
                .id(broadcastId)
                .type(request.getType().toUpperCase().trim())
                .title(request.getTitle().trim())
                .message(request.getMessage().trim())
                .targetRoles(targetRolesStr)
                .recipientCount(notifications.size())
                .sentAt(new Date())
                .build();
    }

    /**
     * Retrieve the history of all past broadcasts.
     */
    public List<BroadcastResponse> getBroadcastHistory() {
        List<Object[]> rows = notificationRepository.findBroadcastSummaries();
        return rows.stream()
                .map(row -> {
                    Date sentAtDate = null;
                    if (row[6] instanceof java.time.Instant) {
                        sentAtDate = Date.from((java.time.Instant) row[6]);
                    } else if (row[6] instanceof Date) {
                        sentAtDate = (Date) row[6];
                    }
                    return BroadcastResponse.builder()
                            .id((UUID) row[0])
                            .type((String) row[1])
                            .title((String) row[2])
                            .message((String) row[3])
                            .targetRoles((String) row[4])
                            .recipientCount((Long) row[5])
                            .sentAt(sentAtDate)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Revoke / recall a sent broadcast, soft-deleting all associated user notifications.
     */
    @Transactional
    public void revokeBroadcast(UUID broadcastId) {
        notificationRepository.softDeleteByBroadcastId(broadcastId);
    }

    private NotificationResponse toNotificationResponse(NotificationEntity notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .actionUrl(notification.getActionUrl())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
