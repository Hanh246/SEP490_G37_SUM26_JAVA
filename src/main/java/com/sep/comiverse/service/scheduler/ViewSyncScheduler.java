package com.sep.comiverse.service.scheduler;

import com.fasterxml.uuid.Generators;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReadingHistoryService readingHistoryService;
    private final ComicCrudPlugin comicCrudPlugin;

    public static final String COMIC_VIEW_HASH = "comic:view:counter";
    public static final String CHAPTER_VIEW_HASH = "chapter:view:counter";

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    @Transactional
    public void flushViewsToPostgres() {
        LocalDate today = LocalDate.now();

        syncComicViews(today);

        syncChapterViews();

        readingHistoryService.syncReadingHistoryFromRedis();
    }

    @Scheduled(cron = "0 0 0 * * *") // Runs daily at midnight
    public void cleanOldReadingHistories() {
        readingHistoryService.cleanOldHistory();
    }

    private void syncComicViews(LocalDate today) {
        Set<Object> comicIds = redisTemplate.opsForHash().keys(COMIC_VIEW_HASH);
        if (comicIds.isEmpty()) return;

        String upsertDailySql = """
            INSERT INTO comic_daily_views (id, comic_id, log_date, view_count, deleted, create_at, update_at)
            VALUES (CAST(:id AS uuid), CAST(:comicId AS uuid), :logDate, :viewCount, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (comic_id, log_date)
            DO UPDATE SET view_count = comic_daily_views.view_count + EXCLUDED.view_count, update_at = CURRENT_TIMESTAMP
        """;

        String updateGlobalSql = """
            UPDATE comics 
            SET view_count = view_count + :viewCount 
            WHERE id = CAST(:comicId AS uuid)
        """;

        for (Object idObj : comicIds) {
            String comicIdStr = (String) idObj;

            Number rawVal = (Number) redisTemplate.opsForHash().get(COMIC_VIEW_HASH, comicIdStr);
            Integer increments = (rawVal != null) ? rawVal.intValue() : null;
            if (increments == null || increments <= 0) continue;

            redisTemplate.opsForHash().increment(COMIC_VIEW_HASH, comicIdStr, -increments);

            UUID id = Generators.timeBasedEpochGenerator().generate();

            Map<String, Object> params = Map.of(
                    "id", id.toString(),
                    "comicId", comicIdStr,
                    "logDate", today,
                    "viewCount", increments
            );

            jdbcTemplate.update(upsertDailySql, params);
            jdbcTemplate.update(updateGlobalSql, params);

            // Evict cache of comic detail
            try {
                comicCrudPlugin.evictComicCache(UUID.fromString(comicIdStr));
            } catch (Exception e) {
                log.error("Failed to evict comic cache for comicId: {}", comicIdStr, e);
            }
        }
    }

    private void syncChapterViews() {
        Set<Object> chapterIds = redisTemplate.opsForHash().keys(CHAPTER_VIEW_HASH);
        if (chapterIds.isEmpty()) return;

        String updateChapterSql = """
            UPDATE chapters 
            SET view_count = view_count + :viewCount 
            WHERE id = CAST(:chapterId AS uuid)
        """;

        for (Object idObj : chapterIds) {
            String chapterIdStr = (String) idObj;

            Number rawVal = (Number) redisTemplate.opsForHash().get(CHAPTER_VIEW_HASH, chapterIdStr);
            Integer increments = (rawVal != null) ? rawVal.intValue() : null;
            if (increments == null || increments <= 0) continue;

            redisTemplate.opsForHash().increment(CHAPTER_VIEW_HASH, chapterIdStr, -increments);

            Map<String, Object> params = Map.of(
                    "chapterId", chapterIdStr,
                    "viewCount", increments
            );

            jdbcTemplate.update(updateChapterSql, params);
        }
    }
}