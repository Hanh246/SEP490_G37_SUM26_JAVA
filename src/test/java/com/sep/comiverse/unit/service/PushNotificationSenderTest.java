package com.sep.comiverse.unit.service;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.entity.PushDeviceTokenEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.INotificationRepository;
import com.sep.comiverse.repository.IPushDeviceTokenRepository;
import com.sep.comiverse.service.push.NotificationPushEvent;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        UserEntity user = UserEntity.builder().email("reader@example.com").build();
        user.setId(userId);
        PushDeviceTokenEntity device = PushDeviceTokenEntity.builder()
                .user(user)
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
        when(pushDeviceTokenRepository.findActiveByUserIds(anyCollection())).thenReturn(List.of(device));
        when(notificationRepository.countUnreadByUserIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{userId, 7L}));
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

    @Test
    void sendsAWholeBroadcastInOneFirebaseBatchWithPerUserBadges() throws Exception {
        UUID readerId = UUID.randomUUID();
        UUID translatorId = UUID.randomUUID();
        UserEntity reader = UserEntity.builder().email("reader@example.com").build();
        reader.setId(readerId);
        UserEntity translator = UserEntity.builder().email("translator@example.com").build();
        translator.setId(translatorId);
        PushDeviceTokenEntity readerDevice = PushDeviceTokenEntity.builder()
                .user(reader)
                .token("reader-token")
                .platform("android")
                .enabled(true)
                .build();
        PushDeviceTokenEntity translatorDevice = PushDeviceTokenEntity.builder()
                .user(translator)
                .token("translator-token")
                .platform("ios")
                .enabled(true)
                .build();
        NotificationResponse readerNotification = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title("Reader announcement")
                .message("Reader message")
                .type("INFO")
                .build();
        NotificationResponse translatorNotification = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title("Translator announcement")
                .message("Translator message")
                .type("WARNING")
                .build();
        BatchResponse batchResponse = mock(BatchResponse.class);

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(pushDeviceTokenRepository.findActiveByUserIds(anyCollection()))
                .thenReturn(List.of(readerDevice, translatorDevice));
        when(notificationRepository.countUnreadByUserIds(anyCollection()))
                .thenReturn(List.of(new Object[]{readerId, 2L}, new Object[]{translatorId, 5L}));
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(List.of());

        sender.sendToUsers(List.of(
                new NotificationPushEvent(readerId, readerNotification),
                new NotificationPushEvent(translatorId, translatorNotification)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging).sendEach(messages.capture());
        assertThat(messages.getValue()).hasSize(2);
        String payload = GsonFactory.getDefaultInstance().toString(messages.getValue());
        assertThat(payload)
                .contains("Reader announcement")
                .contains("Translator announcement")
                .contains("\"badge\":2")
                .contains("\"badge\":5");
    }

    @Test
    void splitsLargeBroadcastsAtTheFirebaseFiveHundredMessageLimit() throws Exception {
        int recipientCount = 501;
        List<UserEntity> users = IntStream.range(0, recipientCount)
                .mapToObj(index -> {
                    UserEntity user = UserEntity.builder()
                            .email("reader" + index + "@example.com")
                            .build();
                    user.setId(UUID.randomUUID());
                    return user;
                })
                .toList();
        List<PushDeviceTokenEntity> devices = IntStream.range(0, recipientCount)
                .mapToObj(index -> PushDeviceTokenEntity.builder()
                        .user(users.get(index))
                        .token("token-" + index)
                        .platform("android")
                        .enabled(true)
                        .build())
                .toList();
        List<NotificationPushEvent> deliveries = IntStream.range(0, recipientCount)
                .mapToObj(index -> new NotificationPushEvent(
                        users.get(index).getId(),
                        NotificationResponse.builder()
                                .id(UUID.randomUUID())
                                .title("Announcement " + index)
                                .message("Message " + index)
                                .type("INFO")
                                .build()
                ))
                .toList();
        List<Object[]> unreadRows = users.stream()
                .map(user -> new Object[]{user.getId(), 1L})
                .toList();
        BatchResponse batchResponse = mock(BatchResponse.class);

        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(pushDeviceTokenRepository.findActiveByUserIds(anyCollection())).thenReturn(devices);
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(unreadRows);
        when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(List.of());

        sender.sendToUsers(deliveries);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> batches = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging, times(2)).sendEach(batches.capture());
        assertThat(batches.getAllValues())
                .extracting(List::size)
                .containsExactly(500, 1);
    }
}
