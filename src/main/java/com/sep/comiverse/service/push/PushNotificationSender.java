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
import java.util.List;
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
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null || userId == null || notification == null) {
            return;
        }

        List<PushDeviceTokenEntity> devices = pushDeviceTokenRepository.findActiveByUserId(userId);
        if (devices.isEmpty()) {
            return;
        }
        int unreadBadge = toBadgeCount(notificationRepository.countUnreadByUserId(userId));

        for (int start = 0; start < devices.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, devices.size());
            sendBatch(messaging, devices.subList(start, end), notification, unreadBadge);
        }
    }

    private void sendBatch(
            FirebaseMessaging messaging,
            List<PushDeviceTokenEntity> devices,
            NotificationResponse notification,
            int unreadBadge
    ) {
        List<Message> messages = devices.stream()
                .map(device -> buildMessage(device.getToken(), notification, unreadBadge))
                .toList();
        try {
            BatchResponse response = messaging.sendEach(messages);
            removeUnregisteredTokens(devices, response.getResponses());
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
}
