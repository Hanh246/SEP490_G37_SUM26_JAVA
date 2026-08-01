package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private IUserRepository userRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, redisTemplate);
    }

    @Test
    void findUserByIdReturnsCachedSnapshotWithoutDatabaseLookup() {
        UUID userId = UUID.randomUUID();
        String cacheKey = "user:snapshot:" + userId;
        UserSnapshot cached = new UserSnapshot(userId, "Reader One", "avatar.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cached);

        UserSnapshot result = service.findUserById(userId);

        assertSame(cached, result);
        verifyNoInteractions(userRepository);
        verify(valueOperations, never()).set(cacheKey, cached, Duration.ofHours(1));
    }

    @Test
    void findUserByIdLoadsAndCachesSnapshotOnCacheMiss() {
        UUID userId = UUID.randomUUID();
        String cacheKey = "user:snapshot:" + userId;
        UserSnapshot databaseValue = new UserSnapshot(userId, "Reader One", "avatar.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(userRepository.findUserSnapshotById(userId)).thenReturn(Optional.of(databaseValue));

        UserSnapshot result = service.findUserById(userId);

        assertSame(databaseValue, result);
        verify(valueOperations).set(cacheKey, databaseValue, Duration.ofHours(1));
    }

    @Test
    void findUserByIdFallsBackToDatabaseAndEvictsCorruptCacheValue() {
        UUID userId = UUID.randomUUID();
        String cacheKey = "user:snapshot:" + userId;
        UserSnapshot databaseValue = new UserSnapshot(userId, "Reader One", "avatar.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("wrong-cache-type");
        when(userRepository.findUserSnapshotById(userId)).thenReturn(Optional.of(databaseValue));

        UserSnapshot result = service.findUserById(userId);

        assertSame(databaseValue, result);
        verify(redisTemplate).delete(cacheKey);
        verify(valueOperations).set(cacheKey, databaseValue, Duration.ofHours(1));
    }

    @Test
    void findUserByIdStillWorksWhenRedisIsUnavailable() {
        UUID userId = UUID.randomUUID();
        String cacheKey = "user:snapshot:" + userId;
        UserSnapshot databaseValue = new UserSnapshot(userId, "Reader One", "avatar.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis unavailable"));
        when(userRepository.findUserSnapshotById(userId)).thenReturn(Optional.of(databaseValue));

        UserSnapshot result = service.findUserById(userId);

        assertSame(databaseValue, result);
        verify(redisTemplate).delete(cacheKey);
    }

    @Test
    void findUserByIdReturnsBusinessErrorWhenDatabaseRecordIsMissing() {
        UUID userId = UUID.randomUUID();
        String cacheKey = "user:snapshot:" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(userRepository.findUserSnapshotById(userId)).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class, () -> service.findUserById(userId));

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("User not found", error.getMessage());
    }
}
