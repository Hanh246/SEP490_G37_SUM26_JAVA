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
    public static final String COMIC_RATING_COUNT_HASH = "comic:rating:count:counter";
    public static final String COMIC_RATING_SUM_HASH = "comic:rating:sum:counter";

    @Scheduled(fixedRate = 3600000) // Runs every 1 hour
    @Transactional
    public void flushInteractionsToPostgres() {
        syncComicLikes();
        syncComicSaves();
        syncComicRatings();
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

    private void syncComicRatings() {
        Set<Object> countComicIds = redisTemplate.opsForHash().keys(COMIC_RATING_COUNT_HASH);
        Set<Object> sumComicIds = redisTemplate.opsForHash().keys(COMIC_RATING_SUM_HASH);

        java.util.Set<Object> allComicIds = new java.util.HashSet<>();
        if (countComicIds != null) allComicIds.addAll(countComicIds);
        if (sumComicIds != null) allComicIds.addAll(sumComicIds);

        if (allComicIds.isEmpty()) return;

        String syncRatingSql = """
            UPDATE comics c
            SET rating_average = COALESCE((
                SELECT ROUND(CAST(AVG(ur.score) AS numeric), 1)
                FROM user_ratings ur
                WHERE ur.comic_id = c.id AND ur.deleted = false
            ), 0.0),
            rating_count = COALESCE((
                SELECT COUNT(ur.id)
                FROM user_ratings ur
                WHERE ur.comic_id = c.id AND ur.deleted = false
            ), 0)
            WHERE c.id = CAST(:comicId AS uuid)
        """;

        for (Object idObj : allComicIds) {
            String comicIdStr = (String) idObj;

            Number rawCountVal = (Number) redisTemplate.opsForHash().get(COMIC_RATING_COUNT_HASH, comicIdStr);
            Number rawSumVal = (Number) redisTemplate.opsForHash().get(COMIC_RATING_SUM_HASH, comicIdStr);

            int countInc = (rawCountVal != null) ? rawCountVal.intValue() : 0;
            double sumInc = (rawSumVal != null) ? rawSumVal.doubleValue() : 0.0;

            if (countInc == 0 && sumInc == 0.0) continue;

            if (countInc != 0) {
                redisTemplate.opsForHash().increment(COMIC_RATING_COUNT_HASH, comicIdStr, -countInc);
            }
            if (sumInc != 0.0) {
                redisTemplate.opsForHash().increment(COMIC_RATING_SUM_HASH, comicIdStr, -sumInc);
            }

            Map<String, Object> params = Map.of("comicId", comicIdStr);
            jdbcTemplate.update(syncRatingSql, params);

            try {
                comicCrudPlugin.evictComicCache(UUID.fromString(comicIdStr));
            } catch (Exception e) {
                log.error("Failed to evict comic cache for comicId: {}", comicIdStr, e);
            }
        }
    }
}

