package com.sep.comiverse.unit.service;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.PushDeviceTokenEntity;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IPushDeviceTokenRepository;
import com.sep.comiverse.service.push.PushNotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationSenderTest {

    @Mock
    private IPushDeviceTokenRepository pushDeviceTokenRepository;
    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    @Mock
    private FirebaseMessaging firebaseMessaging;

    private PushNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new PushNotificationSender(
                pushDeviceTokenRepository,
                notificationRepository,
                firebaseMessagingProvider
        );
    }

    @Test
    void sendsIosAlertWithCurrentUnreadBadgeCount() throws Exception {
        UUID userId = UUID.randomUUID();
        PushDeviceTokenEntity device = PushDeviceTokenEntity.builder()
                .token("ios-fcm-token")
                .platform("ios")
                .enabled(true)
                .build();
        NotificationResponse notification = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title("New reply")
                .message("Someone replied to your thread")
                .type("FORUM")
                .build();
        BatchResponse batchResponse = mock(BatchResponse.class);

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(pushDeviceTokenRepository.findActiveByUserId(userId)).thenReturn(List.of(device));
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(7L);
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(List.of());

        sender.sendToUser(userId, notification);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging).sendEach(messages.capture());
        String payload = GsonFactory.getDefaultInstance().toString(messages.getValue().getFirst());
        assertThat(payload)
                .contains("\"apns-priority\":\"10\"")
                .contains("\"apns-push-type\":\"alert\"")
                .contains("\"badge\":7")
                .contains("\"sound\":\"default\"");
        assertThat(payload)
                .contains("\"channel_id\":\"comiverse_activity\"")
                .contains("\"priority\":\"high\"");
    }
}
