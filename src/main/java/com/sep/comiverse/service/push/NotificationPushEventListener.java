package com.sep.comiverse.service.push;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationPushEventListener {

    private final PushNotificationSender pushNotificationSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCommitted(NotificationPushEvent event) {
        pushNotificationSender.sendToUser(event.userId(), event.notification());
    }
}
