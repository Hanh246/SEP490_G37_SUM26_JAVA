package com.sep.comiverse.unit.plugin.crud;

import com.sep.comiverse.plugin.crud.ComicCrudPlugin;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
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

        String cacheKey = "comic:detail:v2:" + comicId.toString();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedDto);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertNotNull(result);
        assertEquals("Cached Comic", result.getTitle());
        assertEquals(100L, result.getViewCount());
        verify(comicRepository, never()).findByIdWithGenres(any());
    }


    @Test
    void testGetComicDetail_CacheHitWithoutAuthorName_RebuildsCreatorMetadataFromDb() {
        UUID authorId = UUID.randomUUID();

        ComicDTO staleCachedDto = new ComicDTO();
        staleCachedDto.setId(comicId);
        staleCachedDto.setAuthorId(authorId);
        staleCachedDto.setAuthorName(null);
        staleCachedDto.setViewCount(10L);
        staleCachedDto.setLikeCount(0);
        staleCachedDto.setSaveCount(0);

        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);
        comic.setAuthorId(authorId);

        ComicDTO refreshedDto = new ComicDTO();
        refreshedDto.setId(comicId);
        refreshedDto.setAuthorId(authorId);
        refreshedDto.setAuthorName("Public Pen Name");
        refreshedDto.setViewCount(10L);
        refreshedDto.setLikeCount(0);
        refreshedDto.setSaveCount(0);

        String cacheKey = "comic:detail:v2:" + comicId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(staleCachedDto);
        when(comicRepository.findByIdWithGenres(comicId)).thenReturn(Optional.of(comic));
        when(mapperPlugin.toDto(comic)).thenReturn(refreshedDto);

        ComicDTO result = comicCrudPlugin.getComicDetail(comicId);

        assertEquals("Public Pen Name", result.getAuthorName());
        verify(comicRepository).findByIdWithGenres(comicId);
        verify(valueOperations).set(eq(cacheKey), eq(refreshedDto), any(Duration.class));
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

        String cacheKey = "comic:detail:v2:" + comicId.toString();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(comicRepository.findByIdWithGenres(comicId)).thenReturn(Optional.of(comic));
        when(mapperPlugin.toDto(comic)).thenReturn(loadedDto);

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
    void testUpdateComic_AdminManagedFieldsMatchMergedComicEntity() {
        ComicEntity existing = new ComicEntity();
        existing.setId(comicId);
        existing.setTitle("Existing Comic");
        existing.setLanguage("Japanese");
        existing.setMinimumAge(13);
        existing.setPublicationStatus(ComicPublicationStatus.ONGOING);
        existing.setModerationStatus(ComicModerationStatus.DRAFT);

        UUID genreId = UUID.randomUUID();
        GenreEntity genre = new GenreEntity();
        genre.setId(genreId);
        genre.setName("Action");

        ComicDTO request = new ComicDTO();
        request.setLanguage("English");
        request.setMinimumAge(16);
        request.setPublicationStatus(ComicPublicationStatus.COMPLETED);
        request.setModerationStatus(ComicModerationStatus.PUBLISHED);
        request.setGenreIds(List.of(genreId));

        ComicDTO mapped = new ComicDTO();
        mapped.setId(comicId);
        mapped.setLanguage("English");
        mapped.setMinimumAge(16);
        mapped.setPublicationStatus(ComicPublicationStatus.COMPLETED);
        mapped.setModerationStatus(ComicModerationStatus.PUBLISHED);

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(existing));
        when(genreRepository.findAllById(List.of(genreId))).thenReturn(List.of(genre));
        when(comicRepository.save(existing)).thenReturn(existing);
        when(mapperPlugin.toDto(existing)).thenReturn(mapped);

        ComicDTO result = comicCrudPlugin.update(comicId, request);

        assertEquals("English", existing.getLanguage());
        assertEquals(16, existing.getMinimumAge());
        assertEquals(ComicPublicationStatus.COMPLETED, existing.getPublicationStatus());
        assertEquals(ComicModerationStatus.PUBLISHED, existing.getModerationStatus());
        assertEquals(1, existing.getGenres().size());
        assertTrue(existing.getGenres().contains(genre));
        assertEquals(ComicModerationStatus.PUBLISHED, result.getModerationStatus());
        verify(genreRepository).findAllById(List.of(genreId));
        verify(comicRepository).save(existing);
    }

    @Test
    void testUpdateComic_RejectsUnknownComicBeforeApplyingMergedFields() {
        when(comicRepository.findById(comicId)).thenReturn(Optional.empty());
        ComicDTO request = new ComicDTO();
        request.setModerationStatus(ComicModerationStatus.PUBLISHED);

        RuntimeException error = assertThrows(RuntimeException.class, () -> comicCrudPlugin.update(comicId, request));

        assertEquals("Comic not found", error.getMessage());
        verify(comicRepository, never()).save(any());
        verifyNoInteractions(genreRepository);
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

    @Test
    void testGetComicDetailBySlug_SlugCacheHit() {
        String slug = "invincible-sword-god";
        String slugCacheKey = "comic:slug:" + slug;
        String detailCacheKey = "comic:detail:v2:" + comicId.toString();

        ComicDTO cachedDto = new ComicDTO();
        cachedDto.setId(comicId);
        cachedDto.setTitle("Invincible Sword God");
        cachedDto.setSlug(slug);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(slugCacheKey)).thenReturn(comicId.toString());
        when(valueOperations.get(detailCacheKey)).thenReturn(cachedDto);

        ComicDTO result = comicCrudPlugin.getComicDetailBySlug(slug);

        assertNotNull(result);
        assertEquals(comicId, result.getId());
        assertEquals("Invincible Sword God", result.getTitle());
        assertEquals(slug, result.getSlug());
        verify(comicRepository, never()).findBySlugWithGenres(any());
    }

    @Test
    void testGetComicDetailBySlug_SlugCacheMiss_DbHit() {
        String slug = "spirit-recovery";
        String slugCacheKey = "comic:slug:" + slug;

        ComicEntity entity = new ComicEntity();
        entity.setId(comicId);
        entity.setTitle("Spirit Recovery");
        entity.setSlug(slug);

        ComicDTO dto = new ComicDTO();
        dto.setId(comicId);
        dto.setTitle("Spirit Recovery");
        dto.setSlug(slug);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(slugCacheKey)).thenReturn(null);
        when(comicRepository.findBySlugWithGenres(slug)).thenReturn(Optional.of(entity));
        when(comicRepository.findByIdWithGenres(comicId)).thenReturn(Optional.of(entity));
        when(mapperPlugin.toDto(entity)).thenReturn(dto);

        ComicDTO result = comicCrudPlugin.getComicDetailBySlug(slug);

        assertNotNull(result);
        assertEquals(comicId, result.getId());
        assertEquals("Spirit Recovery", result.getTitle());
        verify(valueOperations).set(eq(slugCacheKey), eq(comicId.toString()), any(Duration.class));
    }

    @Test
    void testGetComicDetailBySlug_NotFound_ThrowsException() {
        String slug = "non-existent-comic";
        String slugCacheKey = "comic:slug:" + slug;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(slugCacheKey)).thenReturn(null);
        when(comicRepository.findBySlugWithGenres(slug)).thenReturn(Optional.empty());

        com.sep.comiverse.exception.CustomException exception = assertThrows(
                com.sep.comiverse.exception.CustomException.class,
                () -> comicCrudPlugin.getComicDetailBySlug(slug)
        );

        assertEquals(404, exception.getCode());
        assertEquals("Comic not found", exception.getMessage());
    }

    @Test
    void testGenerateSlugsForExistingComics() {
        ComicEntity comic1 = new ComicEntity();
        comic1.setId(UUID.randomUUID());
        comic1.setTitle("Test Comic One");
        comic1.setSlug(null);

        ComicEntity comic2 = new ComicEntity();
        comic2.setId(UUID.randomUUID());
        comic2.setTitle("Test Comic One");
        comic2.setSlug(null);

        when(comicRepository.findAll()).thenReturn(List.of(comic1, comic2));

        java.util.Map<String, Object> result = comicCrudPlugin.generateSlugsForExistingComics(false);

        assertNotNull(result);
        assertEquals(2, result.get("updatedCount"));
        assertEquals("test-comic-one", comic1.getSlug());
        assertEquals("test-comic-one-1", comic2.getSlug());
        verify(comicRepository, times(2)).save(any());
    }

    @Test
    void testGenerateSlug_UnicodeAndFullwidth() {
        assertEquals("new-game", ComicEntity.generateSlug("ＮＥＷ　ＧＡＭＥ！"));
        assertEquals("거짓말쟁이-미군과-고장난-마짱", ComicEntity.generateSlug("거짓말쟁이 미군과 고장난 마짱"));
        assertEquals("가지와-알타이르", ComicEntity.generateSlug("가지와 알타이르"));
        assertEquals("1fの騎士", ComicEntity.generateSlug("１Ｆの騎士"));
        assertEquals("dau-la-dai-luc", ComicEntity.generateSlug("Đấu La Đại Lục"));
    }
}
