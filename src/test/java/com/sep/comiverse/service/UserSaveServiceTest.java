package com.sep.comiverse.service;

import com.sep.comiverse.entity.UserSaveEntity;
import com.sep.comiverse.repository.IUserSaveRepository;
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
public class UserSaveServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IUserSaveRepository userSaveRepository;

    private UserSaveService userSaveService;

    private final UUID comicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userSaveService = new UserSaveService(redisTemplate, userSaveRepository);
    }

    @Test
    void testIsComicSavedByUser_NullUser() {
        boolean result = userSaveService.isComicSavedByUser(comicId, null);
        assertFalse(result);
        verify(userSaveRepository, never()).existsByComicIdAndUserId(any(), any());
    }

    @Test
    void testIsComicSavedByUser_InDb() {
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertTrue(result);
        verify(userSaveRepository).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testIsComicSavedByUser_NotInDb() {
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userSaveService.isComicSavedByUser(comicId, userId);

        assertFalse(result);
        verify(userSaveRepository).existsByComicIdAndUserId(comicId, userId);
    }

    @Test
    void testToggleSaveComic_Save() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(false);

        boolean result = userSaveService.toggleSaveComic(comicId, userId);

        assertTrue(result);
        verify(userSaveRepository).save(any(UserSaveEntity.class));
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicId.toString(), 1);
    }

    @Test
    void testToggleSaveComic_Unsave() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(userSaveRepository.existsByComicIdAndUserId(comicId, userId)).thenReturn(true);
        UserSaveEntity mockSave = new UserSaveEntity(userId, comicId);
        when(userSaveRepository.findByComicIdAndUserId(comicId, userId)).thenReturn(Optional.of(mockSave));

        boolean result = userSaveService.toggleSaveComic(comicId, userId);

        assertFalse(result);
        verify(userSaveRepository).delete(mockSave);
        verify(hashOperations).increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicId.toString(), -1);
    }

    @Test
    void testGetSavedComicIds() {
        List<UUID> expectedIds = List.of(UUID.randomUUID());
        when(userSaveRepository.findSavedComicIdsByUserId(userId)).thenReturn(expectedIds);

        List<UUID> result = userSaveService.getSavedComicIds(userId);

        assertEquals(expectedIds, result);
        verify(userSaveRepository).findSavedComicIdsByUserId(userId);
    }
}

