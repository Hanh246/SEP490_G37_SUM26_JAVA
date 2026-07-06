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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserInteractionSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public static final String COMIC_LIKE_USERS_SET_PREFIX = "comic:like:users:";
    public static final String COMIC_LIKE_HASH = "comic:like:counter";
    public static final String COMIC_LIKE_SYNC_ADD = "comic:like:sync:add";
    public static final String COMIC_LIKE_SYNC_REMOVE = "comic:like:sync:remove";

    public static final String COMIC_SAVE_USERS_SET_PREFIX = "comic:save:users:";
    public static final String COMIC_SAVE_HASH = "comic:save:counter";
    public static final String COMIC_SAVE_SYNC_ADD = "comic:save:sync:add";
    public static final String COMIC_SAVE_SYNC_REMOVE = "comic:save:sync:remove";

    @Scheduled(fixedRate = 3600000)// Runs every 1 hour
    @Transactional
    public void flushInteractionsToPostgres() {
        syncComicLikes();

        syncComicSaves();
    }

    private void syncComicLikes() {
        // 1. Process pending additions
        Set<Object> likesToAdd = redisTemplate.opsForSet().members(COMIC_LIKE_SYNC_ADD);
        if (likesToAdd != null && !likesToAdd.isEmpty()) {
            String insertLikeSql = """
                INSERT INTO user_likes (id, user_id, comic_id, deleted, create_at, update_at)
                VALUES (CAST(:id AS uuid), CAST(:userId AS uuid), CAST(:comicId AS uuid), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, comic_id) DO NOTHING
            """;
            for (Object itemObj : likesToAdd) {
                String item = (String) itemObj;
                String[] parts = item.split(":");
                if (parts.length == 2) {
                    String comicId = parts[0];
                    String userId = parts[1];
                    UUID id = com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
                    jdbcTemplate.update(insertLikeSql, Map.of(
                        "id", id.toString(),
                        "userId", userId,
                        "comicId", comicId
                    ));
                }
                redisTemplate.opsForSet().remove(COMIC_LIKE_SYNC_ADD, item);
            }
        }

        // 2. Process pending removals
        Set<Object> likesToRemove = redisTemplate.opsForSet().members(COMIC_LIKE_SYNC_REMOVE);
        if (likesToRemove != null && !likesToRemove.isEmpty()) {
            String deleteLikeSql = """
                DELETE FROM user_likes 
                WHERE user_id = CAST(:userId AS uuid) AND comic_id = CAST(:comicId AS uuid)
            """;
            for (Object itemObj : likesToRemove) {
                String item = (String) itemObj;
                String[] parts = item.split(":");
                if (parts.length == 2) {
                    String comicId = parts[0];
                    String userId = parts[1];
                    jdbcTemplate.update(deleteLikeSql, Map.of(
                        "userId", userId,
                        "comicId", comicId
                    ));
                }
                redisTemplate.opsForSet().remove(COMIC_LIKE_SYNC_REMOVE, item);
            }
        }

        // 3. Process the counter offsets
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
        // 1. Process pending additions
        Set<Object> savesToAdd = redisTemplate.opsForSet().members(COMIC_SAVE_SYNC_ADD);
        if (savesToAdd != null && !savesToAdd.isEmpty()) {
            String insertSaveSql = """
                INSERT INTO user_saves (id, user_id, comic_id, deleted, create_at, update_at)
                VALUES (CAST(:id AS uuid), CAST(:userId AS uuid), CAST(:comicId AS uuid), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, comic_id) DO NOTHING
            """;
            for (Object itemObj : savesToAdd) {
                String item = (String) itemObj;
                String[] parts = item.split(":");
                if (parts.length == 2) {
                    String comicId = parts[0];
                    String userId = parts[1];
                    UUID id = com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
                    jdbcTemplate.update(insertSaveSql, Map.of(
                        "id", id.toString(),
                        "userId", userId,
                        "comicId", comicId
                    ));
                }
                redisTemplate.opsForSet().remove(COMIC_SAVE_SYNC_ADD, item);
            }
        }

        // 2. Process pending removals
        Set<Object> savesToRemove = redisTemplate.opsForSet().members(COMIC_SAVE_SYNC_REMOVE);
        if (savesToRemove != null && !savesToRemove.isEmpty()) {
            String deleteSaveSql = """
                DELETE FROM user_saves 
                WHERE user_id = CAST(:userId AS uuid) AND comic_id = CAST(:comicId AS uuid)
            """;
            for (Object itemObj : savesToRemove) {
                String item = (String) itemObj;
                String[] parts = item.split(":");
                if (parts.length == 2) {
                    String comicId = parts[0];
                    String userId = parts[1];
                    jdbcTemplate.update(deleteSaveSql, Map.of(
                        "userId", userId,
                        "comicId", comicId
                    ));
                }
                redisTemplate.opsForSet().remove(COMIC_SAVE_SYNC_REMOVE, item);
            }
        }

        // 3. Process the counter offsets
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
