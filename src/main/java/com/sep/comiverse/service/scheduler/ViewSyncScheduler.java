package com.sep.comiverse.service.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ViewSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public static final String COMIC_VIEW_HASH = "comic:view:counter";
    public static final String CHAPTER_VIEW_HASH = "chapter:view:counter";

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    @Transactional
    public void flushViewsToPostgres() {
        LocalDate today = LocalDate.now();

        syncComicViews(today);

        syncChapterViews();
    }

    private void syncComicViews(LocalDate today) {
        Set<Object> comicIds = redisTemplate.opsForHash().keys(COMIC_VIEW_HASH);
        if (comicIds.isEmpty()) return;

        String upsertDailySql = """
            INSERT INTO comic_daily_views (comic_id, log_date, view_count)
            VALUES (CAST(:comicId AS uuid), :logDate, :viewCount)
            ON CONFLICT (comic_id, log_date)
            DO UPDATE SET view_count = comic_daily_views.view_count + EXCLUDED.view_count
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

            Map<String, Object> params = Map.of(
                    "comicId", comicIdStr,
                    "logDate", today,
                    "viewCount", increments
            );

            jdbcTemplate.update(upsertDailySql, params);
            jdbcTemplate.update(updateGlobalSql, params);
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