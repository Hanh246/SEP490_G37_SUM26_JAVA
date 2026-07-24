package com.sep.comiverse.service;

import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String USER_SNAPSHOT_CACHE_PREFIX = "user:snapshot:";

    private final IUserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;


    public UserSnapshot findUserById(UUID userId){
        String cacheKey = USER_SNAPSHOT_CACHE_PREFIX + userId.toString();
        UserSnapshot snapshot = null;
        try {
            snapshot = (UserSnapshot) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
            }
        }

        if (snapshot == null) {
            snapshot = userRepository.findUserSnapshotById(userId)
                    .orElseThrow(() -> new CustomException(400, "User not found", HttpStatus.BAD_REQUEST));
            try {
                redisTemplate.opsForValue().set(cacheKey, snapshot, Duration.ofHours(1));
            } catch (Exception e) {
                // Ignore Redis set errors
            }
        }
        return snapshot;
    }
}
