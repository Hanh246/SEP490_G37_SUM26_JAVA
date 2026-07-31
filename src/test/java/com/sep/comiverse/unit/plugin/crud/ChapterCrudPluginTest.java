package com.sep.comiverse.unit.plugin.crud;

import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.ReadingHistoryCacheDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.plugin.IMapperPluginDetail;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.ChapterPremiumPolicyService;
import com.sep.comiverse.service.PremiumPlanService;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.plugin.core.PluginRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChapterCrudPluginTest {

    @Mock
    private IChapterRepository chapterRepository;

    @Mock
    private PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PremiumPlanService premiumPlanService;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IMapperPluginDetail<ChapterEntity, ChapterDTO, UUID> mapperPlugin;
    @Mock
    private ChapterPremiumPolicyService chapterPremiumPolicyService;
    private ChapterCrudPlugin chapterCrudPlugin;

    private final UUID chapterId = UUID.randomUUID();
    private final UUID comicId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String clientIp = "127.0.0.1";

    @BeforeEach
    void setUp() {
        when(pluginRegistry.getPluginFor(ChapterEntity.class)).thenReturn(Optional.of(mapperPlugin));
        when(chapterPremiumPolicyService.isPremiumChapter(anyString()))
                .thenAnswer(invocation -> "2".equals(invocation.getArgument(0)));
        chapterCrudPlugin = new ChapterCrudPlugin(
                chapterRepository,
                pluginRegistry,
                redisTemplate,
                userRepository,
                premiumPlanService,
                chapterPremiumPolicyService
        );
    }

    @Test
    void testGetChapterDetail_CacheHit_FreeChapter() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);

        ChapterEntity entity = new ChapterEntity();
        entity.setId(chapterId);
        entity.setComic(comic);
        entity.setChapterNumber("1");
        entity.setTitle("Free Chapter");
        entity.setViewCount(100L);
        entity.setIsPremium(false);
        entity.setModerationStatus(ChapterStatus.PUBLISHED);
        entity.setImages(List.of("img1.png", "img2.png"));

        when(chapterRepository.findByIdAndDeletedFalse(chapterId))
                .thenReturn(Optional.of(entity));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String userIdentity = "user:" + userId;
        String lockKey = String.format("view:lock:%s:chapter:%s", userIdentity, chapterId);
        when(valueOperations.setIfAbsent(lockKey, "1", Duration.ofMinutes(10))).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString())).thenReturn(10);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        ChapterDTO result = chapterCrudPlugin.getChapterDetail(chapterId, userId, clientIp);

        assertNotNull(result);
        assertEquals("Free Chapter", result.getTitle());
        assertEquals(110L, result.getViewCount()); // 100 + 10
        assertEquals(2, result.getImages().size()); // Free chapter, images not masked

        verify(hashOperations).increment(ViewSyncScheduler.COMIC_VIEW_HASH, comicId.toString(), 1L);
        verify(hashOperations).increment(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString(), 1L);
        ReadingHistoryCacheDTO expectedDto = ReadingHistoryCacheDTO.builder()
                .comicId(comicId)
                .chapterId(chapterId)
                .userId(userId)
                .build();
        verify(setOperations).add("reading:history:sync:queue", expectedDto);
    }

    @Test
    void testGetChapterDetail_CacheMiss_PremiumChapter_Authorized() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);

        ChapterEntity entity = new ChapterEntity();
        entity.setId(chapterId);
        entity.setComic(comic);
        entity.setChapterNumber("2");
        entity.setTitle("Premium Chapter");
        entity.setViewCount(200L);
        entity.setIsPremium(true);
        entity.setModerationStatus(ChapterStatus.PUBLISHED);
        entity.setImages(List.of("img1.png", "img2.png"));

        when(chapterRepository.findByIdAndDeletedFalse(chapterId))
                .thenReturn(Optional.of(entity));

        // Mock authorization
        UserEntity user = new UserEntity();
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(premiumPlanService.hasActivePremium(user)).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String userIdentity = "user:" + userId;
        String lockKey = String.format("view:lock:%s:chapter:%s", userIdentity, chapterId);
        when(valueOperations.setIfAbsent(lockKey, "1", Duration.ofMinutes(10))).thenReturn(false);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        ChapterDTO result = chapterCrudPlugin.getChapterDetail(chapterId, userId, clientIp);

        assertNotNull(result);
        assertEquals("Premium Chapter", result.getTitle());
        assertEquals(2, result.getImages().size()); // Authorized premium, images not masked
        verify(hashOperations, never()).increment(any(), any(), anyLong());
    }

    @Test
    void testGetChapterDetail_PremiumChapter_Unauthorized() {
        ComicEntity comic = new ComicEntity();
        comic.setId(comicId);

        ChapterEntity entity = new ChapterEntity();
        entity.setId(chapterId);
        entity.setComic(comic);
        entity.setChapterNumber("2");
        entity.setTitle("Premium Chapter");
        entity.setViewCount(200L);
        entity.setIsPremium(true);
        entity.setModerationStatus(ChapterStatus.PUBLISHED);
        entity.setImages(List.of("img1.png", "img2.png"));

        when(chapterRepository.findByIdAndDeletedFalse(chapterId))
                .thenReturn(Optional.of(entity));

        // Mock unauthorized
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.empty());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        ChapterDTO result = chapterCrudPlugin.getChapterDetail(chapterId, userId, clientIp);

        assertNotNull(result);
        assertTrue(result.getImages().isEmpty()); // Unauthorized premium, images masked
    }

    @Test
    void testGetChaptersByComicId() {
        ChapterLiteDTO liteDto = ChapterLiteDTO.builder()
                .id(chapterId)
                .comicId(comicId)
                .chapterNumber("1")
                .title("Ch 1")
                .viewCount(50L)
                .isPremium(false)
                .createdAt(java.time.Instant.now())
                .build();

        when(chapterRepository.findChapterMetadataByComicId(comicId, ChapterStatus.PUBLISHED))
                .thenReturn(List.of(liteDto));

        // Redis mocks
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString())).thenReturn(5);

        List<ChapterLiteDTO> result = chapterCrudPlugin.getChaptersByComicId(comicId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(55L, result.get(0).getViewCount()); // 50 + 5
        assertEquals("Ch 1", result.get(0).getTitle());
        assertEquals("1", result.get(0).getChapterNumber());
        assertFalse(result.get(0).getIsPremium());
    }
}
