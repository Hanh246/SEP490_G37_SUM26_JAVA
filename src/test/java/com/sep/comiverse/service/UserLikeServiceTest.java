package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserLikeServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IUserLikeRepository userLikeRepository;

    private UserLikeService userLikeService;

    private final UUID comicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private String userSetKey;
    private String itemKey;

    @BeforeEach
    void setUp() {
        userLikeService = new UserLikeService(redisTemplate, userLikeRepository);
        userSetKey = UserInteractionSyncScheduler.COMIC_LIKE_USERS_SET_PREFIX + comicId.toString();
        itemKey = comicId.toString() + ":" + userId.toString();
    }

    @Test
    void testIsComicLikedByUser_PendingRemove() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(true);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertFalse(result);
        verify(setOperations, never()).isMember(userSetKey, userId.toString());
    }

    @Test
    void testIsComicLikedByUser_InRedisSet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(true);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertTrue(result);
        verify(setOperations, never()).isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey);
    }

    @Test
    void testIsComicLikedByUser_PendingAdd() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey)).thenReturn(true);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertTrue(result);
        verify(userLikeRepository, never()).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testIsComicLikedByUser_InDb() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey)).thenReturn(false);
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);

        boolean result = userLikeService.isComicLikedByUser(comicId, userId);

        assertTrue(result);
        verify(setOperations).add(userSetKey, userId.toString());
    }

    @Test
    void testToggleLikeComic_Like() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Mock check first: not liked
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey)).thenReturn(false);
        when(userLikeRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userLikeService.toggleLikeComic(comicId, userId);

        assertTrue(result);
        verify(setOperations).add(userSetKey, userId.toString());
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicId.toString(), 1);
        verify(setOperations).add(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey);
        verify(setOperations).remove(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey);
    }

    @Test
    void testToggleLikeComic_Unlike() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Mock check first: liked
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(true);

        boolean result = userLikeService.toggleLikeComic(comicId, userId);

        assertFalse(result);
        verify(setOperations).remove(userSetKey, userId.toString());
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicId.toString(), -1);
        verify(setOperations).add(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_REMOVE, itemKey);
        verify(setOperations).remove(UserInteractionSyncScheduler.COMIC_LIKE_SYNC_ADD, itemKey);
    }
}
