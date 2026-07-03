package com.sep.comiverse.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserInteractionSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Redis Target Counter Hashes (Offsets)
    private static final String COMIC_LIKE_HASH = "comic:like:counter";
    private static final String COMIC_SAVE_HASH = "comic:save:counter";

    // Redis Source Deduplication Sets
    private static final String COMIC_LIKE_USERS_SET_PREFIX = "comic:like:users:";
    private static final String COMIC_SAVE_USERS_SET_PREFIX = "comic:save:users:";

    @Scheduled(fixedRate = 3600000)// Runs every 1 hour
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

            Integer increments = (Integer) redisTemplate.opsForHash().get(COMIC_LIKE_HASH, comicIdStr);
            if (increments == null || increments == 0) continue;
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, -increments);

            // Update total numeric count inside the main comics table
            Map<String, Object> params = Map.of(
                    "comicId", comicIdStr,
                    "likeIncrement", increments
            );
            jdbcTemplate.update(updateGlobalComicLikeSql, params);

            // WIPE OUT RISK OF MEMORY BLOAT: Clear the user cache set for this comic.
            // Since the total counter offset has successfully been flattened and saved into DB,
            // we delete the tracking Set key to reclaim Redis RAM.
            String likeSetKey = COMIC_LIKE_USERS_SET_PREFIX + comicIdStr;
            redisTemplate.delete(likeSetKey);
        }
    }

    private void syncComicSaves() {
        // Find all comic IDs that currently have save/bookmark adjustments waiting
        Set<Object> comicIds = redisTemplate.opsForHash().keys(COMIC_SAVE_HASH);
        if (comicIds == null || comicIds.isEmpty()) return;

        String updateGlobalComicSaveSql = """
            UPDATE comics 
            SET save_count = save_count + :saveIncrement 
            WHERE id = CAST(:comicId AS uuid)
        """;

        for (Object idObj : comicIds) {
            String comicIdStr = (String) idObj;

            Integer increments = (Integer) redisTemplate.opsForHash().get(COMIC_SAVE_HASH, comicIdStr);
            if (increments == null || increments == 0) continue;
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, -increments);

            // Update total numeric bookmark count inside the main comics table
            Map<String, Object> params = Map.of(
                    "comicId", comicIdStr,
                    "saveIncrement", increments
            );
            jdbcTemplate.update(updateGlobalComicSaveSql, params);

            // WIPE OUT RISK OF MEMORY BLOAT: Reclaim memory for the save tracking set
            String saveSetKey = COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
            redisTemplate.delete(saveSetKey);
        }
    }
}
