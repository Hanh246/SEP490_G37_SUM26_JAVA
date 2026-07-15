package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaderboardSchedulerTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ComicCrudPlugin comicCrudPlugin;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private LeaderboardScheduler leaderboardScheduler;

    @BeforeEach
    void setUp() {
        leaderboardScheduler = new LeaderboardScheduler(jdbcTemplate, comicCrudPlugin, redisTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testComputeLeaderboards_Success() {
        UUID validComicId = UUID.randomUUID();
        ComicDTO comicDTO = new ComicDTO();
        comicDTO.setId(validComicId);
        comicDTO.setTitle("Valid Comic");

        // Mock JDBC call
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(validComicId));

        // Mock ComicCrudPlugin call
        when(comicCrudPlugin.getComicDetail(validComicId)).thenReturn(comicDTO);

        // Mock Redis calls
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        leaderboardScheduler.computeLeaderboards();

        // Verify query was called 3 times (day, week, month)
        verify(jdbcTemplate, times(3)).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        // Verify details fetched 3 times
        verify(comicCrudPlugin, times(3)).getComicDetail(validComicId);

        // Verify cache saved with results for day, week, month
        ArgumentCaptor<List<ComicDTO>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(valueOperations, times(3)).set(anyString(), listCaptor.capture());

        List<List<ComicDTO>> savedLists = listCaptor.getAllValues();
        assertEquals(3, savedLists.size());
        for (List<ComicDTO> list : savedLists) {
            assertTrue(list instanceof java.util.ArrayList);
            assertEquals(1, list.size());
            assertEquals("Valid Comic", list.get(0).getTitle());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testComputeLeaderboards_GracefulErrorHandling() {
        UUID validComicId = UUID.randomUUID();
        UUID invalidComicId = UUID.randomUUID();

        ComicDTO comicDTO = new ComicDTO();
        comicDTO.setId(validComicId);
        comicDTO.setTitle("Valid Comic");

        // Mock JDBC call returns both IDs
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(validComicId, invalidComicId));

        // Mock ComicCrudPlugin: valid succeeds, invalid throws Exception (soft-deleted comic)
        when(comicCrudPlugin.getComicDetail(validComicId)).thenReturn(comicDTO);
        when(comicCrudPlugin.getComicDetail(invalidComicId)).thenThrow(new RuntimeException("Comic not found"));

        // Mock Redis calls
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        leaderboardScheduler.computeLeaderboards();

        // Verify query was called 3 times
        verify(jdbcTemplate, times(3)).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        // Verify cache saved, filtering out the invalid comic
        ArgumentCaptor<List<ComicDTO>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(valueOperations, times(3)).set(anyString(), listCaptor.capture());

        List<List<ComicDTO>> savedLists = listCaptor.getAllValues();
        assertEquals(3, savedLists.size());
        for (List<ComicDTO> list : savedLists) {
            // Should contain only the valid comic, invalid one is skipped
            assertTrue(list instanceof java.util.ArrayList);
            assertEquals(1, list.size());
            assertEquals("Valid Comic", list.get(0).getTitle());
        }
    }
}
