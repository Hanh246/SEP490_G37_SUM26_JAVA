package com.sep.comiverse.service;

import com.sep.comiverse.entity.UserLikeEntity;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserLikeServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IUserLikeRepository userLikeRepository;

    private UserLikeService userLikeService;

    private final UUID comicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userLikeService = new UserLikeService(redisTemplate, userLikeRepository);
    }

    @Test
    void testIsComicLikedByUser_NullUser() {
        boolean result = userLikeService.isComicLikedByUser(comicId, null);
        assertFalse(result);
        verify(userLikeRepository, never()).existsByComicIdAndUserId(any(), any());
    }

    @Test
    void testIsComicLikedByUser_InDb() {
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertTrue(result);
        verify(userLikeRepository).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testIsComicLikedByUser_NotInDb() {
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertFalse(result);
        verify(userLikeRepository).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testToggleLikeComic_Like() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userLikeService.toggleLikeComic(comicId, userId);

        assertTrue(result);
        verify(userLikeRepository).save(any(UserLikeEntity.class));
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicId.toString(), 1);
    }

    @Test
    void testToggleLikeComic_Unlike() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);
        UserLikeEntity mockLike = new UserLikeEntity(userId, comicId);
        when(userLikeRepository.findByComicIdAndUserId(comicId, userId)).thenReturn(Optional.of(mockLike));

        boolean result = userLikeService.toggleLikeComic(comicId, userId);

        assertFalse(result);
        verify(userLikeRepository).delete(mockLike);
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicId.toString(), -1);
    }

    @Test
    void testGetLikedComicIds() {
        List<UUID> expectedIds = List.of(UUID.randomUUID());
        when(userLikeRepository.findLikedComicIdsByUserId(userId)).thenReturn(expectedIds);

        List<UUID> result = userLikeService.getLikedComicIds(userId);

        assertEquals(expectedIds, result);
        verify(userLikeRepository).findLikedComicIdsByUserId(userId);
    }
}

