package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class LeaderboardScheduler {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ComicCrudPlugin comicCrudPlugin;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final String LEADERBOARD_CACHE_KEY_PREFIX = "comic:leaderboard:";

    @Scheduled(fixedRate = 900000) // Recalculates every 15 minutes
    public void computeLeaderboards() {
        cacheRankingsForTimeframe("day", LocalDate.now(), LocalDate.now());
        cacheRankingsForTimeframe("week", LocalDate.now().minusDays(7), LocalDate.now());
        cacheRankingsForTimeframe("month", LocalDate.now().minusMonths(1), LocalDate.now());
    }

    private void cacheRankingsForTimeframe(String timeframe, LocalDate startDate, LocalDate endDate) {
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

        List<UUID> topComicIds = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                rs.getObject("comic_id", UUID.class)
        );

        List<ComicDTO> leaderboardList = new java.util.ArrayList<>();
        for (UUID id : topComicIds) {
            try {
                ComicDTO detail = comicCrudPlugin.getComicDetail(id);
                if (detail != null) {
                    leaderboardList.add(detail);
                }
            } catch (Exception e) {
                // Ignore mapping errors
            }
        }

        String cacheKey = LEADERBOARD_CACHE_KEY_PREFIX + timeframe;
        redisTemplate.opsForValue().set(cacheKey, leaderboardList);
    }
}