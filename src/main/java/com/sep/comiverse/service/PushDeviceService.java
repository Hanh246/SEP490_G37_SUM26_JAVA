package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.RegisterPushDeviceRequest;
import com.sep.comiverse.entity.PushDeviceTokenEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPushDeviceTokenRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final IPushDeviceTokenRepository pushDeviceTokenRepository;
    private final IUserRepository userRepository;

    @Transactional
    public void register(UUID userId, RegisterPushDeviceRequest request) {
        UserEntity user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        String token = request.getToken().trim();
        PushDeviceTokenEntity device = pushDeviceTokenRepository.findByToken(token)
                .orElseGet(PushDeviceTokenEntity::new);

        // A Firebase installation can change accounts on the same device. The
        // unique token must always belong to the currently authenticated user.
        device.setUser(user);
        device.setToken(token);
        device.setPlatform(request.getPlatform().trim().toLowerCase(Locale.ROOT));
        device.setDeviceName(normalize(request.getDeviceName()));
        device.setEnabled(true);
        device.setDeleted(false);
        device.setLastSeenAt(Instant.now());
        pushDeviceTokenRepository.save(device);
    }

    @Transactional
    public void unregister(UUID userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        pushDeviceTokenRepository.findByToken(rawToken.trim())
                .filter(device -> device.getUser() != null && userId.equals(device.getUser().getId()))
                .ifPresent(pushDeviceTokenRepository::delete);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
