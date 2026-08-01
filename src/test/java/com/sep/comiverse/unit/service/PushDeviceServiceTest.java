package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.RegisterPushDeviceRequest;
import com.sep.comiverse.entity.PushDeviceTokenEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IPushDeviceTokenRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.PushDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

    @Mock
    private IPushDeviceTokenRepository pushDeviceTokenRepository;
    @Mock
    private IUserRepository userRepository;

    private PushDeviceService service;

    @BeforeEach
    void setUp() {
        service = new PushDeviceService(pushDeviceTokenRepository, userRepository);
    }

    @Test
    void registrationMovesExistingInstallationToAuthenticatedAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity currentUser = activeUser(userId);
        PushDeviceTokenEntity existing = PushDeviceTokenEntity.builder()
                .user(activeUser(UUID.randomUUID()))
                .token("old")
                .platform("ios")
                .enabled(false)
                .build();
        existing.setDeleted(true);

        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(currentUser));
        when(pushDeviceTokenRepository.findByToken("fcm-token")).thenReturn(Optional.of(existing));

        service.register(userId, new RegisterPushDeviceRequest(" fcm-token ", "ANDROID", null));

        ArgumentCaptor<PushDeviceTokenEntity> captor = ArgumentCaptor.forClass(PushDeviceTokenEntity.class);
        verify(pushDeviceTokenRepository).save(captor.capture());
        PushDeviceTokenEntity saved = captor.getValue();
        assertEquals(userId, saved.getUser().getId());
        assertEquals("fcm-token", saved.getToken());
        assertEquals("android", saved.getPlatform());
        assertTrue(saved.getEnabled());
        assertEquals(false, saved.getDeleted());
    }

    @Test
    void accountCanOnlyUnregisterItsOwnInstallation() {
        UUID ownerId = UUID.randomUUID();
        PushDeviceTokenEntity device = PushDeviceTokenEntity.builder()
                .user(activeUser(UUID.randomUUID()))
                .token("fcm-token")
                .platform("android")
                .build();
        when(pushDeviceTokenRepository.findByToken("fcm-token")).thenReturn(Optional.of(device));

        service.unregister(ownerId, "fcm-token");

        verify(pushDeviceTokenRepository, never()).delete(device);
    }

    private UserEntity activeUser(UUID id) {
        UserEntity user = UserEntity.builder()
                .email(id + "@example.com")
                .status("ACTIVE")
                .build();
        user.setId(id);
        user.setDeleted(false);
        return user;
    }
}
