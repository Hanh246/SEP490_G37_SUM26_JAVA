package com.sep.comiverse.service.push;

import java.util.List;

public record NotificationPushBatchEvent(List<NotificationPushEvent> deliveries) {

    public NotificationPushBatchEvent {
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
    }
}
