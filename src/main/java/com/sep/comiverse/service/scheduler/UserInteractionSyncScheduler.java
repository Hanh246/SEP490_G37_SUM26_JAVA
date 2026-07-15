package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserInteractionSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ComicCrudPlugin comicCrudPlugin;

    public static final String COMIC_LIKE_HASH = "comic:like:counter";
    public static final String COMIC_SAVE_HASH = "comic:save:counter";

    @Scheduled(fixedRate = 3600000) // Runs every 1 hour
    @Transactional
    public void flushInteractionsToPostgres() {
        syncComicLikes();
        syncComicSaves();
    }

    private void syncComicLikes() {
        Set<Object> comicIds = redisTemplate.opsForHash().keys(COMIC_LIKE_HASH);
        if (comicIds == null || comicIds.isEmpty()) return;

        String updateGlobalComicLikeSql = """
            UPDATE comics 
            SET like_count = like_count + :likeIncrement 
            WHERE id = CAST(:comicId AS uuid)
        """;

        for (Object idObj : comicIds) {
            String comicIdStr = (String) idObj;

            Number rawVal = (Number) redisTemplate.opsForHash().get(COMIC_LIKE_HASH, comicIdStr);
            Integer increments = (rawVal != null) ? rawVal.intValue() : null;
            if (increments == null || increments == 0) continue;

            // Atomically decrement/reset the counter offset in Redis
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, -increments);

            // Update total numeric count inside the main comics table
            Map<String, Object> params = Map.of(
                    "comicId", comicIdStr,
                    "likeIncrement", increments
            );
            jdbcTemplate.update(updateGlobalComicLikeSql, params);

            // Evict cache of comic detail
            try {
                comicCrudPlugin.evictComicCache(UUID.fromString(comicIdStr));
            } catch (Exception e) {
                log.error("Failed to evict comic cache for comicId: {}", comicIdStr, e);
            }
        }
    }

    private void syncComicSaves() {
        Set<Object> comicIds = redisTemplate.opsForHash().keys(COMIC_SAVE_HASH);
        if (comicIds == null || comicIds.isEmpty()) return;

        String updateGlobalComicSaveSql = """
            UPDATE comics 
            SET save_count = save_count + :saveIncrement 
            WHERE id = CAST(:comicId AS uuid)
        """;

        for (Object idObj : comicIds) {
            String comicIdStr = (String) idObj;

            Number rawVal = (Number) redisTemplate.opsForHash().get(COMIC_SAVE_HASH, comicIdStr);
            Integer increments = (rawVal != null) ? rawVal.intValue() : null;
            if (increments == null || increments == 0) continue;

            // Atomically decrement/reset the counter offset in Redis
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, -increments);

            // Update total numeric bookmark count inside the main comics table
            Map<String, Object> params = Map.of(
                    "comicId", comicIdStr,
                    "saveIncrement", increments
            );
            jdbcTemplate.update(updateGlobalComicSaveSql, params);

            // Evict cache of comic detail
            try {
                comicCrudPlugin.evictComicCache(UUID.fromString(comicIdStr));
            } catch (Exception e) {
                log.error("Failed to evict comic cache for comicId: {}", comicIdStr, e);
            }
        }
    }
}

