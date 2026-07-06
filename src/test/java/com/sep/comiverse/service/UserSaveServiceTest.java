package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserSaveRepository;
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
public class UserSaveServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IUserSaveRepository userSaveRepository;

    private UserSaveService userSaveService;

    private final UUID comicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private String userSetKey;
    private String itemKey;

    @BeforeEach
    void setUp() {
        userSaveService = new UserSaveService(redisTemplate, userSaveRepository);
        userSetKey = UserInteractionSyncScheduler.COMIC_SAVE_USERS_SET_PREFIX + comicId.toString();
        itemKey = comicId.toString() + ":" + userId.toString();
    }

    @Test
    void testIsComicSavedByUser_PendingRemove() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(true);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertFalse(result);
        verify(setOperations, never()).isMember(userSetKey, userId.toString());
    }

    @Test
    void testIsComicSavedByUser_InRedisSet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(true);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertTrue(result);
        verify(setOperations, never()).isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey);
    }

    @Test
    void testIsComicSavedByUser_PendingAdd() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey)).thenReturn(true);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertTrue(result);
        verify(userSaveRepository, never()).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testIsComicSavedByUser_InDb() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey)).thenReturn(false);
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertTrue(result);
        verify(setOperations).add(userSetKey, userId.toString());
    }

    @Test
    void testToggleSaveComic_Save() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Mock check first: not saved
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(false);
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey)).thenReturn(false);
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userSaveService.toggleSaveComic(comicId, userId);

        assertTrue(result);
        verify(setOperations).add(userSetKey, userId.toString());
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicId.toString(), 1);
        verify(setOperations).add(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey);
        verify(setOperations).remove(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey);
    }

    @Test
    void testToggleSaveComic_Unsave() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Mock check first: saved
        when(setOperations.isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey)).thenReturn(false);
        when(setOperations.isMember(userSetKey, userId.toString())).thenReturn(true);

        boolean result = userSaveService.toggleSaveComic(comicId, userId);

        assertFalse(result);
        verify(setOperations).remove(userSetKey, userId.toString());
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicId.toString(), -1);
        verify(setOperations).add(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, itemKey);
        verify(setOperations).remove(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, itemKey);
    }
}
