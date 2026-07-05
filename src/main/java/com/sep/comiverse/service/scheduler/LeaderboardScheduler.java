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

    private static final String LEADERBOARD_CACHE_KEY_PREFIX = "comic:leaderboard:";

    @Scheduled(fixedRate = 900000) // Recalculates every 15 minutes
    public void computeLeaderboards() {
        cacheRankingsForTimeframe("day", LocalDate.now(), LocalDate.now());
        cacheRankingsForTimeframe("week", LocalDate.now().minusDays(7), LocalDate.now());
        cacheRankingsForTimeframe("month", LocalDate.now().minusMonths(1), LocalDate.now());
    }

    private void cacheRankingsForTimeframe(String timeframe, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT comic_id, SUM(view_count) as total_views
            FROM comic_daily_views
            WHERE log_date BETWEEN :startDate AND :endDate
            GROUP BY comic_id
            ORDER BY total_views DESC
            LIMIT 20
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        List<UUID> topComicIds = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                UUID.fromString(rs.getString("comic_id"))
        );

        List<ComicDTO> leaderboardList = topComicIds.stream()
                .map(comicCrudPlugin::getComicDetail)
                .toList();

        String cacheKey = LEADERBOARD_CACHE_KEY_PREFIX + timeframe;
        redisTemplate.opsForValue().set(cacheKey, leaderboardList);
    }
}