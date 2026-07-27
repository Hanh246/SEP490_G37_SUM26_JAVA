package com.sep.comiverse.service;

import com.sep.comiverse.dto.ReadingHistoryCacheDTO;
import com.sep.comiverse.entity.ReadingHistoryEntity;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadingHistoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IReadingHistoryRepository readingHistoryRepository;
    private final JwtTokenUtil jwtTokenUtil;

    public static final String READING_HISTORY_SYNC_QUEUE = "reading:history:sync:queue";

    public List<UUID> getReadChapters(UUID comicId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UUID> dbChapterIds = readingHistoryRepository.findReadChapterIdsByUserIdAndComicId(userId, comicId);
        java.util.Set<UUID> allReadChapterIds = new java.util.HashSet<>(dbChapterIds);
        try {
            Set<Object> queuedEntries = redisTemplate.opsForSet().members(READING_HISTORY_SYNC_QUEUE);
            if (queuedEntries != null && !queuedEntries.isEmpty()) {
                for (Object obj : queuedEntries) {
                    if (obj instanceof ReadingHistoryCacheDTO entry) {
                        if (comicId.equals(entry.getComicId()) && userId.equals(entry.getUserId())) {
                            if (entry.getChapterId() != null) {
                                allReadChapterIds.add(entry.getChapterId());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return new java.util.ArrayList<>(allReadChapterIds);
    }

    public boolean isChapterRead(UUID chapterId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return readingHistoryRepository.existsByChapterIdAndUserId(chapterId, userId);
    }

    @Transactional
    public void syncReadingHistoryFromRedis() {
        Set<Object> queued = redisTemplate.opsForSet().members(READING_HISTORY_SYNC_QUEUE);
        if (queued == null || queued.isEmpty()) {
            return;
        }

        for (Object obj : queued) {
            if (obj instanceof ReadingHistoryCacheDTO entry) {
                try {
                    UUID comicId = entry.getComicId();
                    UUID chapterId = entry.getChapterId();
                    UUID userId = entry.getUserId();

                    if (comicId != null && chapterId != null && userId != null) {
                        // Save or update reading history in DB
                        Optional<ReadingHistoryEntity> existing = readingHistoryRepository.findByChapterIdAndUserId(chapterId, userId);
                        if (existing.isEmpty()) {
                            ReadingHistoryEntity newHistory = ReadingHistoryEntity.builder()
                                    .userId(userId)
                                    .comicId(comicId)
                                    .chapterId(chapterId)
                                    .build();
                            readingHistoryRepository.save(newHistory);
                        } else {
                            ReadingHistoryEntity history = existing.get();
                            if (Boolean.TRUE.equals(history.getDeleted())) {
                                history.setDeleted(false);
                            }
                            // Save updates updatedAt timestamp
                            readingHistoryRepository.save(history);
                        }
                    }
                } catch (Exception e) {
                    // Log and ignore individual entry processing failures
                }
                // Remove from Redis queue
                redisTemplate.opsForSet().remove(READING_HISTORY_SYNC_QUEUE, entry);
            }
        }
    }

    @Transactional
    public void deleteComicHistory(UUID comicId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        if (userId == null) {
            return;
        }

        // 1. Delete from database
        readingHistoryRepository.deleteByComicIdAndUserId(comicId, userId);

        // 2. Remove from Redis sync queue
        Set<Object> queued = redisTemplate.opsForSet().members(READING_HISTORY_SYNC_QUEUE);
        if (queued != null && !queued.isEmpty()) {
            for (Object obj : queued) {
                if (obj instanceof ReadingHistoryCacheDTO entry) {
                    if (comicId.equals(entry.getComicId()) && userId.equals(entry.getUserId())) {
                        redisTemplate.opsForSet().remove(READING_HISTORY_SYNC_QUEUE, entry);
                    }
                }
            }
        }
    }

    @Transactional
    public void cleanOldHistory() {
        java.time.Instant oneMonthAgo = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        readingHistoryRepository.deleteOldHistoryExceptLatest(oneMonthAgo);
    }

    public long getReadComicCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        java.util.Set<UUID> readComicIds = new java.util.HashSet<>(readingHistoryRepository.findReadComicIdsByUserId(userId));
        try {
            Set<Object> queuedEntries = redisTemplate.opsForSet().members(READING_HISTORY_SYNC_QUEUE);
            if (queuedEntries != null && !queuedEntries.isEmpty()) {
                for (Object obj : queuedEntries) {
                    if (obj instanceof ReadingHistoryCacheDTO entry) {
                        if (userId.equals(entry.getUserId()) && entry.getComicId() != null) {
                            readComicIds.add(entry.getComicId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore Redis connection/read errors
        }
        return readComicIds.size();
    }
}
