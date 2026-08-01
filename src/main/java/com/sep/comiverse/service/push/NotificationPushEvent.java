package com.sep.comiverse.service.push;

import com.sep.comiverse.dto.response.NotificationResponse;

import java.util.UUID;

public record NotificationPushEvent(UUID userId, NotificationResponse notification) {
}
