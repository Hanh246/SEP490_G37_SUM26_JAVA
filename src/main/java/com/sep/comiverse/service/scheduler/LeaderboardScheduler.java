package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardScheduler {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ComicCrudPlugin comicCrudPlugin;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final String LEADERBOARD_CACHE_KEY_PREFIX = "comic:leaderboard:";

    @Scheduled(fixedRate = 900000)
    public void computeLeaderboards() {
        cacheRankingsForDay();
        cacheRankingsForTimeframe("week", LocalDate.now().minusDays(7), LocalDate.now());
        cacheRankingsForTimeframe("month", LocalDate.now().minusMonths(1), LocalDate.now());
    }

    private void cacheRankingsForDay() {
        LocalDate today = LocalDate.now();

        List<UUID> topComicIds = getTopComicIds(today, today);

        if (topComicIds.isEmpty()) {
            LocalDate yesterday = today.minusDays(1);
            topComicIds = getTopComicIds(yesterday, yesterday);
        }

        saveToRedis("day", topComicIds);
    }

    private void cacheRankingsForTimeframe(String timeframe, LocalDate startDate, LocalDate endDate) {
        List<UUID> topComicIds = getTopComicIds(startDate, endDate);
        saveToRedis(timeframe, topComicIds);
    }

    private List<UUID> getTopComicIds(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT cdv.comic_id, SUM(cdv.view_count) as total_views
            FROM comic_daily_views cdv
            INNER JOIN comics c ON cdv.comic_id = c.id
            WHERE cdv.log_date BETWEEN :startDate AND :endDate
              AND c.deleted = false
              AND c.moderation_status = 'PUBLISHED'
            GROUP BY cdv.comic_id
            ORDER BY total_views DESC
            LIMIT 20
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        return jdbcTemplate.query(sql, params, (rs, rowNum) ->
                rs.getObject("comic_id", UUID.class)
        );
    }

    private void saveToRedis(String timeframe, List<UUID> topComicIds) {
        List<ComicDTO> leaderboardList = new ArrayList<>();
        for (UUID id : topComicIds) {
            try {
                ComicDTO detail = comicCrudPlugin.getComicDetail(id);
                if (detail != null) {
                    leaderboardList.add(detail);
                }
            } catch (Exception e) {
                log.error("Can not get comic with ID: {}", id, e);
            }
        }

        String cacheKey = LEADERBOARD_CACHE_KEY_PREFIX + timeframe;
        redisTemplate.opsForValue().set(cacheKey, leaderboardList);
    }
}