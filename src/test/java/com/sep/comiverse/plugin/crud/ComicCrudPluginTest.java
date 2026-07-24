package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.plugin.IMapperPluginDetail;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.IComicRepository;
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
    private IGenreRepository genreRepository;

    @Mock
    private PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;

    @Mock
    private IMapperPluginDetail<ComicEntity, ComicDTO, UUID> mapperPlugin;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private LeaderboardScheduler leaderboardScheduler;

    private ComicCrudPlugin comicCrudPlugin;

    private final UUID comicId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(pluginRegistry.getPluginFor(ComicEntity.class)).thenReturn(Optional.of(mapperPlugin));
        comicCrudPlugin = new ComicCrudPlugin(comicRepository, genreRepository, pluginRegistry, redisTemplate, leaderboardScheduler);
    }

    @Test
    void testGetComicDetail_CacheHit() {
        ComicDTO cachedDto = new ComicDTO();
        cachedDto.setId(comicId);
        cachedDto.setTitle("Cached Comic");
        cachedDto.setViewCount(100L);
        cachedDto.setLikeCount(50);
        cachedDto.setSaveCount(10);

        String cacheKey = "comic:detail:" + comicId.toString();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedDto);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), anyString())).thenReturn(null);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("Cached Comic", result.getTitle());
        assertEquals(100L, result.getViewCount());
        verify(comicRepository, never()).findById(any());
    }

    @Test
    void testGetComicDetail_CacheMiss() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);
        comic.setTitle("DB Comic");
        comic.setViewCount(200L);
        comic.setLikeCount(80);
        comic.setSaveCount(15);

        ComicDTO loadedDto = new ComicDTO();
        loadedDto.setId(comicId);
        loadedDto.setTitle("DB Comic");
        loadedDto.setViewCount(200L);
        loadedDto.setLikeCount(80);
        loadedDto.setSaveCount(15);

        String cacheKey = "comic:detail:" + comicId.toString();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(mapperPlugin.toDto(comic)).thenReturn(loadedDto);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), anyString())).thenReturn(null);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("DB Comic", result.getTitle());
        assertEquals(200L, result.getViewCount());
        verify(valueOperations).set(eq(cacheKey), eq(loadedDto), any(Duration.class));
    }

    @Test
    void testGetCachedLeaderboard_CacheHit() {
        String timeframe = "day";
        String cacheKey = "comic:leaderboard:" + timeframe;
        ComicDTO comic = new ComicDTO();
        comic.setId(comicId);
        List<ComicDTO> list = List.of(comic);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(list);

        List<ComicDTO> result = comicCrudPlugin.getCachedLeaderboard(timeframe);

        assertEquals(1, result.size());
        assertEquals(comicId, result.get(0).getId());
        verify(leaderboardScheduler, never()).computeLeaderboards();
    }

    @Test
    void testGetCachedLeaderboard_CacheMiss_Retries() {
        String timeframe = "day";
        String cacheKey = "comic:leaderboard:" + timeframe;
        ComicDTO comic = new ComicDTO();
        comic.setId(comicId);
        List<ComicDTO> list = List.of(comic);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(list);

        List<ComicDTO> result = comicCrudPlugin.getCachedLeaderboard(timeframe);

        assertEquals(1, result.size());
        assertEquals(comicId, result.get(0).getId());
        verify(leaderboardScheduler, times(1)).computeLeaderboards();
    }
    @Test
    void testUpdateComic_StoresLanguageOnComicEntity() {
        ComicEntity existing = new ComicEntity();
        existing.setId(comicId);
        existing.setTitle("Existing Comic");
        existing.setLanguage("Japanese");

        ComicDTO request = new ComicDTO();
        request.setLanguage(" Korean ");

        ComicDTO mapped = new ComicDTO();
        mapped.setId(comicId);
        mapped.setLanguage("Korean");

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(existing));
        when(comicRepository.save(existing)).thenReturn(existing);
        when(mapperPlugin.toDto(existing)).thenReturn(mapped);

        ComicDTO result = comicCrudPlugin.update(comicId, request);

        assertEquals("Korean", existing.getLanguage());
        assertEquals("Korean", result.getLanguage());
        verify(comicRepository).save(existing);
    }

    @Test
    void testUpdateComic_RejectsBlankLanguage() {
        ComicEntity existing = new ComicEntity();
        existing.setId(comicId);
        existing.setLanguage("Japanese");

        ComicDTO request = new ComicDTO();
        request.setLanguage("   ");

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> comicCrudPlugin.update(comicId, request)
        );

        assertEquals("Comic language cannot be blank", exception.getMessage());
        verify(comicRepository, never()).save(any());
    }

}
