package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.PremiumPlanService;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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

    @Autowired
    public ChapterCrudPlugin(IChapterRepository repository,
                             PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                             RedisTemplate<String, Object> redisTemplate,
                             IUserRepository userRepository,
                             PremiumPlanService premiumPlanService) {
        super(repository, pluginRegistry, ChapterEntity.class);
        this.chapterRepository = repository;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.premiumPlanService = premiumPlanService;
    }

    /**
     * Public chapter detail endpoint.
     * Always loads by PUBLISHED moderation status so draft/pending/rejected chapters are never exposed.
     */
    @Transactional(readOnly = true)
    public ChapterDTO getChapterDetail(UUID chapterId, UUID userId, String clientIp) {
        ChapterEntity entity = chapterRepository
                .findByIdAndDeletedFalseAndModerationStatus(chapterId, ChapterStatus.PUBLISHED)
                .orElseThrow(() -> new RuntimeException("Chapter not found or not published"));

        ChapterDTO responseDto = ChapterDTO.builder()
                .id(entity.getId())
                .comicId(entity.getComic() == null ? null : entity.getComic().getId())
                .chapterNumber(entity.getChapterNumber())
                .title(entity.getTitle())
                .moderationStatus(entity.getModerationStatus())
                .viewCount(entity.getViewCount() == null ? 0L : entity.getViewCount())
                .isPremium(Boolean.TRUE.equals(entity.getIsPremium()))
                .createdAt(entity.getCreatedAt())
                .build();

        if (Boolean.TRUE.equals(responseDto.getIsPremium()) && !checkUserPremiumAccess(userId)) {
            responseDto.setImages(Collections.emptyList());
        } else {
            responseDto.setImages(normalizeImageList(entity.getImages()));
        }

        trackAndIncrementView(responseDto.getComicId(), chapterId, userId, clientIp);

        Number rawChapterViews = (Number) redisTemplate.opsForHash()
                .get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString());
        if (rawChapterViews != null) {
            responseDto.setViewCount(responseDto.getViewCount() + rawChapterViews.longValue());
        }

        return responseDto;
    }

    private void trackAndIncrementView(UUID comicId, UUID chapterId, UUID userId, String clientIp) {
        if (comicId == null || chapterId == null) {
            return;
        }

        String userIdentity = (userId != null) ? "user:" + userId : "guest:ip:" + clientIp;
        String lockKey = String.format("view:lock:%s:chapter:%s", userIdentity, chapterId);

        Boolean isFirstTimeIn10Mins = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", java.time.Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isFirstTimeIn10Mins)) {
            redisTemplate.opsForHash().increment(ViewSyncScheduler.COMIC_VIEW_HASH, comicId.toString(), 1);
            redisTemplate.opsForHash().increment(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString(), 1);
            if (userId != null) {
                redisTemplate.opsForSet().add("reading:history:sync:queue", comicId + ":" + chapterId + ":" + userId);
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

    /**
     * Public chapter list by comic. Only PUBLISHED chapters are returned.
     */
    @Transactional(readOnly = true)
    public List<ChapterLiteDTO> getChaptersByComicId(UUID comicId) {
        List<ChapterLiteDTO> results = chapterRepository.findChapterMetadataByComicIdAndStatus(
                comicId,
                ChapterStatus.PUBLISHED
        );

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream().map(dto -> {
            ChapterLiteDTO copy = ChapterLiteDTO.builder()
                    .id(dto.getId())
                    .comicId(dto.getComicId())
                    .chapterNumber(dto.getChapterNumber())
                    .title(dto.getTitle())
                    .viewCount(dto.getViewCount() == null ? 0L : dto.getViewCount())
                    .isPremium(Boolean.TRUE.equals(dto.getIsPremium()))
                    .createdAt(dto.getCreatedAt())
                    .build();

            Number rawChapterViews = (Number) redisTemplate.opsForHash()
                    .get(ViewSyncScheduler.CHAPTER_VIEW_HASH, copy.getId().toString());
            if (rawChapterViews != null) {
                copy.setViewCount(copy.getViewCount() + rawChapterViews.longValue());
            }
            return copy;
        }).toList();
    }

    /**
     * Compatibility guard for bad legacy data like images = ARRAY['url1,url2,url3'].
     * New uploads save each URL as one element in PostgreSQL text[].
     */
    private List<String> normalizeImageList(List<String> rawImages) {
        if (rawImages == null || rawImages.isEmpty()) {
            return Collections.emptyList();
        }
        if (rawImages.size() == 1 && rawImages.getFirst() != null && rawImages.getFirst().contains(",http")) {
            return Arrays.stream(rawImages.getFirst().split(",(?=https?://)"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        return rawImages;
    }
}
