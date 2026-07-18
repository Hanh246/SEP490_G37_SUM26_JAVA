package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.ReadingHistoryCacheDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.PremiumPlanService;
import com.sep.comiverse.service.ReadingHistoryService;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class ChapterCrudPlugin
        extends AbstractCrudPlugin<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final IChapterRepository chapterRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserRepository userRepository;
    private final PremiumPlanService premiumPlanService;

    private static final String CHAPTER_DETAIL_CACHE_PREFIX = "chapter:detail:meta:";
    private static final String COMIC_CHAPTERS_LIST_CACHE_PREFIX = "comic:chapters:list:";

    @Autowired
    public ChapterCrudPlugin(IChapterRepository repository,
                             PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                             RedisTemplate<String, Object> redisTemplate,
                             IUserRepository userRepository,
                             PremiumPlanService premiumPlanService){
        super(repository, pluginRegistry, ChapterEntity.class);
        this.chapterRepository = repository;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.premiumPlanService = premiumPlanService;
    }

    @Transactional(readOnly = true)
    public ChapterDTO getChapterDetail(UUID chapterId, UUID userId, String clientIp) {
        String cacheKey = CHAPTER_DETAIL_CACHE_PREFIX + chapterId;

        ChapterLiteDTO cacheDto = null;
        try {
            cacheDto = (ChapterLiteDTO) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Delete corrupt cache so it can be rebuilt
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // Ignore
            }
        }
        List<String> images;

        if (cacheDto == null) {
            ChapterEntity entity = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new RuntimeException("Chapter not found"));

            cacheDto = ChapterLiteDTO.builder()
                    .id(entity.getId())
                    .comicId(entity.getComic().getId())
                    .chapterNumber(entity.getChapterNumber())
                    .title(entity.getTitle())
                    .viewCount(entity.getViewCount())
                    .isPremium(entity.getIsPremium())
                    .createdAt(entity.getCreatedAt())
                    .build();

            try {
                redisTemplate.opsForValue().set(cacheKey, cacheDto, Duration.ofDays(3));
            } catch (Exception e) {
                // Ignore Redis set errors
            }
            images = entity.getImages();
        }else {
            List<String> rawImages = chapterRepository.findImagesByChapterIdAndStatus(chapterId);

            if (rawImages != null && rawImages.size() == 1 && rawImages.getFirst().contains(",")) {
                images = java.util.Arrays.asList(rawImages.getFirst().split(","));
            } else {
                images = rawImages != null ? rawImages : Collections.emptyList();
            }
        }

        ChapterDTO responseDto = ChapterDTO.builder()
                .id(cacheDto.getId())
                .comicId(cacheDto.getComicId())
                .chapterNumber(cacheDto.getChapterNumber())
                .title(cacheDto.getTitle())
                .viewCount(cacheDto.getViewCount())
                .isPremium(cacheDto.getIsPremium())
                .createdAt(cacheDto.getCreatedAt())
                .build();

        if (Boolean.TRUE.equals(responseDto.getIsPremium()) && !checkUserPremiumAccess(userId)) {
            responseDto.setImages(Collections.emptyList());
        }else {
            responseDto.setImages(images);
        }

        trackAndIncrementView(responseDto.getComicId(), chapterId, userId, clientIp);

        Number rawChapterViews = (Number) redisTemplate.opsForHash().get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString());
        if (rawChapterViews != null) {
            responseDto.setViewCount(responseDto.getViewCount() + rawChapterViews.intValue());
        }

        return responseDto;
    }

    private void trackAndIncrementView(UUID comicId, UUID chapterId, UUID userId, String clientIp) {
        String userIdentity = (userId != null) ? "user:" + userId : "guest:ip:" + clientIp;

        String lockKey = String.format("view:lock:%s:chapter:%s", userIdentity, chapterId);

        Boolean isFirstTimeIn10Mins = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isFirstTimeIn10Mins)) {
            redisTemplate.opsForHash().increment(ViewSyncScheduler.COMIC_VIEW_HASH, comicId.toString(), 1);
            redisTemplate.opsForHash().increment(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString(), 1);
            if (userId != null) {
                ReadingHistoryCacheDTO historyDto = ReadingHistoryCacheDTO.builder()
                        .comicId(comicId)
                        .chapterId(chapterId)
                        .userId(userId)
                        .build();
                redisTemplate.opsForSet().add(ReadingHistoryService.READING_HISTORY_SYNC_QUEUE, historyDto);
            }
        }
    }

    private boolean checkUserPremiumAccess(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findByIdWithRole(userId)
                .map(premiumPlanService::hasActivePremium)
                .orElse(false);
    }

    private ChapterDTO maskPremiumImages(ChapterDTO dto) {
        return ChapterDTO.builder()
                .id(dto.getId())
                .comicId(dto.getComicId())
                .chapterNumber(dto.getChapterNumber())
                .title(dto.getTitle())
                .viewCount(dto.getViewCount())
                .isPremium(dto.getIsPremium())
                .createdAt(dto.getCreatedAt())
                .num(dto.getNum())
                .date(dto.getDate())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<ChapterLiteDTO> getChaptersByComicId(UUID comicId) {
        String cacheKey = COMIC_CHAPTERS_LIST_CACHE_PREFIX + comicId.toString();

        List<ChapterLiteDTO> cachedResults = null;
        try {
            cachedResults = (List<ChapterLiteDTO>) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Delete corrupt cache so it can be rebuilt
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // Ignore
            }
        }

        if (cachedResults == null) {
            cachedResults = chapterRepository.findChapterMetadataByComicId(comicId, ChapterStatus.PUBLISHED);

            if (cachedResults != null && !cachedResults.isEmpty()) {
                List<ChapterLiteDTO> listToCache = new java.util.ArrayList<>(cachedResults);
                try {
                    redisTemplate.opsForValue().set(cacheKey, listToCache, Duration.ofHours(3));
                } catch (Exception e) {
                    // Ignore Redis set errors
                }
            } else {
                return Collections.emptyList();
            }
        }
        return cachedResults.stream().map(dto -> {
            ChapterLiteDTO copy = ChapterLiteDTO.builder()
                    .id(dto.getId())
                    .comicId(dto.getComicId())
                    .chapterNumber(dto.getChapterNumber())
                    .title(dto.getTitle())
                    .viewCount(dto.getViewCount())
                    .isPremium(dto.getIsPremium())
                    .createdAt(dto.getCreatedAt())
                    .build();

                Number rawChapterViews = (Number) redisTemplate.opsForHash().get(ViewSyncScheduler.CHAPTER_VIEW_HASH, copy.getId().toString());
                if (rawChapterViews != null) {
                    copy.setViewCount(copy.getViewCount() + rawChapterViews.intValue());
                }
            return copy;
        }).toList();
    }

    public void evictChaptersCache(UUID comicId) {
        String cacheKey = COMIC_CHAPTERS_LIST_CACHE_PREFIX + comicId.toString();
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // Ignore/log error
        }
    }

    public void evictChapterDetailCache(UUID chapterId) {
        String cacheKey = CHAPTER_DETAIL_CACHE_PREFIX + chapterId.toString();
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // Ignore/log error
        }
    }
}
