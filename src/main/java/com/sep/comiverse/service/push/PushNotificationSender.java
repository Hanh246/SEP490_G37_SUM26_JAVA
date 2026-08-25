package com.sep.comiverse.service.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.PushDeviceTokenEntity;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IPushDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSender {

    static final String ANDROID_CHANNEL_ID = "comiverse_activity";
    private static final int MAX_BATCH_SIZE = 500;

    private final IPushDeviceTokenRepository pushDeviceTokenRepository;
    private final INotificationRepository notificationRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Async("pushNotificationExecutor")
    @Transactional
    public void sendToUser(UUID userId, NotificationResponse notification) {
        sendToUsersNow(List.of(new NotificationPushEvent(userId, notification)));
    }

    @Transactional
    public void sendToUsers(List<NotificationPushEvent> deliveries) {
        sendToUsersNow(deliveries);
    }

    private void sendToUsersNow(List<NotificationPushEvent> deliveries) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.warn("FCM push skipped: Firebase push is not configured. " +
                    "Set FIREBASE_PUSH_ENABLED=true and provide service-account credentials.");
            return;
        }
        if (deliveries == null || deliveries.isEmpty()) {
            return;
        }

        Map<UUID, NotificationResponse> notificationsByUser = new LinkedHashMap<>();
        for (NotificationPushEvent delivery : deliveries) {
            if (delivery != null && delivery.userId() != null && delivery.notification() != null) {
                notificationsByUser.put(delivery.userId(), delivery.notification());
            }
        }
        if (notificationsByUser.isEmpty()) {
            return;
        }

        List<PushDeviceTokenEntity> devices = pushDeviceTokenRepository
                .findActiveByUserIds(notificationsByUser.keySet());
        if (devices.isEmpty()) {
            return;
        }

        Map<UUID, Integer> unreadBadges = new LinkedHashMap<>();
        List<Object[]> unreadRows = notificationRepository.countUnreadByUserIds(notificationsByUser.keySet());
        if (unreadRows != null) {
            for (Object[] row : unreadRows) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID userId && row[1] instanceof Number count) {
                    unreadBadges.put(userId, toBadgeCount(count.longValue()));
                }
            }
        }

        List<PushTarget> targets = new ArrayList<>();
        for (PushDeviceTokenEntity device : devices) {
            UUID userId = device.getUser() == null ? null : device.getUser().getId();
            NotificationResponse notification = notificationsByUser.get(userId);
            if (notification != null && device.getToken() != null && !device.getToken().isBlank()) {
                targets.add(new PushTarget(
                        device,
                        notification,
                        unreadBadges.getOrDefault(userId, 0)
                ));
            }
        }

        for (int start = 0; start < targets.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, targets.size());
            sendBatch(messaging, targets.subList(start, end));
        }
    }

    private void sendBatch(FirebaseMessaging messaging, List<PushTarget> targets) {
        List<Message> messages = targets.stream()
                .map(target -> buildMessage(
                        target.device().getToken(),
                        target.notification(),
                        target.unreadBadge()
                ))
                .toList();
        try {
            BatchResponse response = messaging.sendEach(messages);
            removeUnregisteredTokens(
                    targets.stream().map(PushTarget::device).toList(),
                    response.getResponses()
            );
            if (response.getFailureCount() > 0) {
                log.warn("FCM delivered {} push messages and failed to deliver {}",
                        response.getSuccessCount(), response.getFailureCount());
            }
        } catch (FirebaseMessagingException exception) {
            // Push transport must never roll back the already committed in-app notification.
            log.warn("FCM batch delivery failed: {}", exception.getMessage());
        }
    }

    private Message buildMessage(String token, NotificationResponse notification, int unreadBadge) {
        String title = safe(notification.getTitle(), "ComiVerse update");
        String body = safe(notification.getMessage(), "You have a new notification.");

        Message.Builder message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(Duration.ofDays(7).toMillis())
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL_ID)
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .putHeader("apns-push-type", "alert")
                        .setAps(Aps.builder()
                                .setBadge(unreadBadge)
                                .setSound("default")
                                .build())
                        .build())
                .putData("notificationId", value(notification.getId()))
                .putData("title", title)
                .putData("body", body)
                .putData("type", safe(notification.getType(), "INFO"))
                .putData("actionUrl", safe(notification.getActionUrl(), ""));
        if (notification.getCreatedAt() != null) {
            message.putData("createdAt", notification.getCreatedAt().toString());
        }
        return message.build();
    }

    private void removeUnregisteredTokens(
            List<PushDeviceTokenEntity> devices,
            List<SendResponse> responses
    ) {
        List<String> invalidTokens = new ArrayList<>();
        for (int index = 0; index < responses.size(); index++) {
            SendResponse response = responses.get(index);
            if (response.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = response.getException();
            if (exception != null && exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                invalidTokens.add(devices.get(index).getToken());
            }
        }
        if (!invalidTokens.isEmpty()) {
            pushDeviceTokenRepository.deleteAllByTokenIn(invalidTokens);
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private int toBadgeCount(long unreadCount) {
        if (unreadCount <= 0) {
            return 0;
        }
        return unreadCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) unreadCount;
    }

    private record PushTarget(
            PushDeviceTokenEntity device,
            NotificationResponse notification,
            int unreadBadge
    ) {
    }
}
