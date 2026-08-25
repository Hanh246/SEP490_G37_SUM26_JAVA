package com.sep.comiverse.service.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushBatchEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationSender pushNotificationSender;

    @Async("pushNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationsCommitted(NotificationPushBatchEvent event) {
        if (event == null || event.deliveries().isEmpty()) {
            return;
        }

        for (NotificationPushEvent delivery : event.deliveries()) {
            try {
                messagingTemplate.convertAndSend(
                        "/topic/notifications/" + delivery.userId(),
                        delivery.notification()
                );
            } catch (RuntimeException exception) {
                // One disconnected browser must not prevent the remaining
                // recipients or mobile devices from receiving the broadcast.
                log.warn("WebSocket notification delivery failed for user {}: {}",
                        delivery.userId(), exception.getMessage());
            }
        }

        pushNotificationSender.sendToUsers(event.deliveries());
    }
}
