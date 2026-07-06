package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.plugin.IMapperPluginDetail;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import com.sep.comiverse.service.scheduler.LeaderboardScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.plugin.core.PluginRegistry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ComicCrudPluginTest {

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IMapperPluginDetail<ComicEntity, ComicDTO, UUID> mapperPlugin;

    private ComicCrudPlugin comicCrudPlugin;

    private final UUID comicId = UUID.randomUUID();
    private final String comicIdStr = comicId.toString();
    private final String cacheKey = "comic:detail:" + comicId;

    @BeforeEach
    void setUp() {
        when(pluginRegistry.getPluginFor(ComicEntity.class)).thenReturn(Optional.of(mapperPlugin));
        comicCrudPlugin = new ComicCrudPlugin(comicRepository, pluginRegistry, redisTemplate);
    }

    @Test
    void testGetComicDetail_CacheHit() {
        ComicDTO cachedDto = new ComicDTO();
        cachedDto.setId(comicId);
        cachedDto.setTitle("Cached Comic");
        cachedDto.setViewCount(100L);
        cachedDto.setLikeCount(50);
        cachedDto.setSaveCount(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedDto);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Mock hash values to check if they are added
        when(hashOperations.get(ViewSyncScheduler.COMIC_VIEW_HASH, comicIdStr)).thenReturn(5);
        when(hashOperations.get(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicIdStr)).thenReturn(2);
        when(hashOperations.get(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr)).thenReturn(1);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("Cached Comic", result.getTitle());
        assertEquals(105L, result.getViewCount());  // 100 + 5
        assertEquals(52, result.getLikeCount());   // 50 + 2
        assertEquals(11, result.getSaveCount());   // 10 + 1

        verify(comicRepository, never()).findById(any());
    }

    @Test
    void testGetComicDetail_CacheMiss() {
        ComicEntity entity = new ComicEntity();
        ComicDTO loadedDto = new ComicDTO();
        loadedDto.setId(comicId);
        loadedDto.setTitle("DB Comic");
        loadedDto.setViewCount(200L);
        loadedDto.setLikeCount(60);
        loadedDto.setSaveCount(20);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(entity));
        when(mapperPlugin.toDto(entity)).thenReturn(loadedDto);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(ViewSyncScheduler.COMIC_VIEW_HASH, comicIdStr)).thenReturn(null);
        when(hashOperations.get(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicIdStr)).thenReturn(null);
        when(hashOperations.get(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr)).thenReturn(null);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("DB Comic", result.getTitle());
        assertEquals(200L, result.getViewCount());
        verify(valueOperations).set(eq(cacheKey), eq(loadedDto), eq(Duration.ofHours(24)));
    }

    @Test
    void testGetCachedLeaderboard_Hit() {
        ComicDTO top1 = new ComicDTO();
        top1.setId(comicId);
        top1.setTitle("Top 1");
        List<ComicDTO> leaderboard = List.of(top1);
        String expectedKey = LeaderboardScheduler.LEADERBOARD_CACHE_KEY_PREFIX + "week";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(leaderboard);

        List<ComicDTO> result = comicCrudPlugin.getCachedLeaderboard("week");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Top 1", result.get(0).getTitle());
    }

    @Test
    void testGetCachedLeaderboard_Miss() {
        String expectedKey = LeaderboardScheduler.LEADERBOARD_CACHE_KEY_PREFIX + "month";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        List<ComicDTO> result = comicCrudPlugin.getCachedLeaderboard("month");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
