package com.sep.comiverse.service;

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
        return readingHistoryRepository.findReadChapterIdsByUserIdAndComicId(userId, comicId);
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
            String entry = (String) obj;
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    UUID comicId = UUID.fromString(parts[0]);
                    UUID chapterId = UUID.fromString(parts[1]);
                    UUID userId = UUID.fromString(parts[2]);

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
                } catch (Exception e) {
                    // Log and ignore individual malformed entry processing failures
                }
            }
            // Remove from Redis queue
            redisTemplate.opsForSet().remove(READING_HISTORY_SYNC_QUEUE, entry);
        }
    }
}
