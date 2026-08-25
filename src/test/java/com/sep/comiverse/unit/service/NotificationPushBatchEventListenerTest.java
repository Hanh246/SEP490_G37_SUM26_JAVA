package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.service.push.NotificationPushBatchEvent;
import com.sep.comiverse.service.push.NotificationPushBatchEventListener;
import com.sep.comiverse.service.push.NotificationPushEvent;
import com.sep.comiverse.service.push.PushNotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPushBatchEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PushNotificationSender pushNotificationSender;

    @Test
    void deliversOnlyToEachUsersPrivateTopicAndQueuesOnePushBatch() {
        UUID readerId = UUID.randomUUID();
        UUID translatorId = UUID.randomUUID();
        NotificationResponse readerNotification = notification("Reader announcement");
        NotificationResponse translatorNotification = notification("Translator announcement");
        List<NotificationPushEvent> deliveries = List.of(
                new NotificationPushEvent(readerId, readerNotification),
                new NotificationPushEvent(translatorId, translatorNotification)
        );
        NotificationPushBatchEventListener listener = new NotificationPushBatchEventListener(
                messagingTemplate,
                pushNotificationSender
        );

        listener.onNotificationsCommitted(new NotificationPushBatchEvent(deliveries));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/notifications/" + readerId),
                eq(readerNotification)
        );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/notifications/" + translatorId),
                eq(translatorNotification)
        );
        verify(pushNotificationSender).sendToUsers(deliveries);
    }

    @Test
    void oneWebSocketFailureDoesNotBlockOtherRecipientsOrMobilePush() {
        List<NotificationPushEvent> deliveries = List.of(
                new NotificationPushEvent(UUID.randomUUID(), notification("First")),
                new NotificationPushEvent(UUID.randomUUID(), notification("Second"))
        );
        doThrow(new IllegalStateException("socket unavailable"))
                .doNothing()
                .when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));
        NotificationPushBatchEventListener listener = new NotificationPushBatchEventListener(
                messagingTemplate,
                pushNotificationSender
        );

        listener.onNotificationsCommitted(new NotificationPushBatchEvent(deliveries));

        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(Object.class));
        verify(pushNotificationSender).sendToUsers(deliveries);
    }

    private NotificationResponse notification(String title) {
        return NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title(title)
                .message("Message")
                .type("INFO")
                .build();
    }
}
