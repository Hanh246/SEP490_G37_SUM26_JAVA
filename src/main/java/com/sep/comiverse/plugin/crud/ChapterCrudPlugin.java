package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
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

import java.time.Duration;
import java.util.UUID;

@Component
public class ChapterCrudPlugin
        extends AbstractCrudPlugin<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final IChapterRepository chapterRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserRepository userRepository;
    private final PremiumPlanService premiumPlanService;

    private static final String CHAPTER_CACHE_PREFIX = "chapter:detail:";

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
        String cacheKey = CHAPTER_CACHE_PREFIX + chapterId;

        ChapterDTO dto = (ChapterDTO) redisTemplate.opsForValue().get(cacheKey);

        if (dto == null) {
            ChapterEntity entity = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new RuntimeException("Chapter not found"));

            dto = plugin.toDto(entity);

            redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofDays(3));
        }

        if (Boolean.TRUE.equals(dto.getIsPremium())) {
            boolean isAuthorized = checkUserPremiumAccess(userId);

            if (!isAuthorized) {
                dto = maskPremiumImages(dto);
            }
        }

        trackAndIncrementView(dto.getComicId(), chapterId, userId, clientIp);

        Number rawChapterViews = (Number) redisTemplate.opsForHash().get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString());
        if (rawChapterViews != null) {
            dto.setViewCount(dto.getViewCount() + rawChapterViews.intValue());
        }

        return dto;
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

    private ChapterDTO maskPremiumImages(ChapterDTO dto) {
        return ChapterDTO.builder()
                .id(dto.getId())
                .comicId(dto.getComicId())
                .chapterNumber(dto.getChapterNumber())
                .title(dto.getTitle())
                .viewCount(dto.getViewCount())
                .isPremium(dto.getIsPremium())
                .createdAt(dto.getCreatedAt())
                .images(java.util.Collections.emptyList())
                .num(dto.getNum())
                .date(dto.getDate())
                .build();
    }
}
