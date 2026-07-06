package com.sep.comiverse.service;

import com.sep.comiverse.entity.ReadingHistoryEntity;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReadingHistoryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private IReadingHistoryRepository readingHistoryRepository;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    private ReadingHistoryService readingHistoryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID comicId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final String syncQueueKey = ReadingHistoryService.READING_HISTORY_SYNC_QUEUE;

    @BeforeEach
    void setUp() {
        readingHistoryService = new ReadingHistoryService(redisTemplate, readingHistoryRepository, jwtTokenUtil);
    }

    @Test
    void testGetReadChapters_Unauthenticated() {
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(null);

        List<UUID> result = readingHistoryService.getReadChapters(comicId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(readingHistoryRepository);
    }

    @Test
    void testGetReadChapters_Success() {
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(userId);
        when(readingHistoryRepository.findReadChapterIdsByUserIdAndComicId(userId, comicId))
                .thenReturn(List.of(chapterId));

        List<UUID> result = readingHistoryService.getReadChapters(comicId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(chapterId, result.get(0));
    }

    @Test
    void testIsChapterRead_Unauthenticated() {
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(null);

        boolean result = readingHistoryService.isChapterRead(chapterId);

        assertFalse(result);
        verify(readingHistoryRepository, never()).existsByChapterIdAndUserId(any(), any());
    }

    @Test
    void testIsChapterRead_True() {
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(userId);
        when(readingHistoryRepository.existsByChapterIdAndUserId(chapterId, userId)).thenReturn(true);

        boolean result = readingHistoryService.isChapterRead(chapterId);

        assertTrue(result);
    }

    @Test
    void testSyncReadingHistoryFromRedis_EmptyQueue() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(syncQueueKey)).thenReturn(Collections.emptySet());

        readingHistoryService.syncReadingHistoryFromRedis();

        verifyNoInteractions(readingHistoryRepository);
    }

    @Test
    void testSyncReadingHistoryFromRedis_NewEntry() {
        String queueValue = comicId + ":" + chapterId + ":" + userId;
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(syncQueueKey)).thenReturn(Set.of(queueValue));
        when(readingHistoryRepository.findByChapterIdAndUserId(chapterId, userId)).thenReturn(Optional.empty());

        readingHistoryService.syncReadingHistoryFromRedis();

        ArgumentCaptor<ReadingHistoryEntity> entityCaptor = ArgumentCaptor.forClass(ReadingHistoryEntity.class);
        verify(readingHistoryRepository).save(entityCaptor.capture());
        ReadingHistoryEntity saved = entityCaptor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(comicId, saved.getComicId());
        assertEquals(chapterId, saved.getChapterId());
        assertFalse(saved.getDeleted());

        verify(setOperations).remove(syncQueueKey, queueValue);
    }

    @Test
    void testSyncReadingHistoryFromRedis_ExistingDeletedEntry() {
        String queueValue = comicId + ":" + chapterId + ":" + userId;
        ReadingHistoryEntity existing = ReadingHistoryEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .chapterId(chapterId)
                .build();
        existing.setDeleted(true);

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(syncQueueKey)).thenReturn(Set.of(queueValue));
        when(readingHistoryRepository.findByChapterIdAndUserId(chapterId, userId)).thenReturn(Optional.of(existing));

        readingHistoryService.syncReadingHistoryFromRedis();

        verify(readingHistoryRepository).save(existing);
        assertFalse(existing.getDeleted());

        verify(setOperations).remove(syncQueueKey, queueValue);
    }
}
