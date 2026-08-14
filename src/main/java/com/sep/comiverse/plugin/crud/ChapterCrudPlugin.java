package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.ReadingHistoryCacheDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.PremiumPlanService;
import com.sep.comiverse.service.ChapterPremiumPolicyService;
import com.sep.comiverse.service.ReadingHistoryService;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ChapterCrudPlugin
        extends AbstractCrudPlugin<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    private final IChapterRepository chapterRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserRepository userRepository;
    private final PremiumPlanService premiumPlanService;
    private final ChapterPremiumPolicyService chapterPremiumPolicyService;
    private final java.util.Map<UUID, String> moderatorNameCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String resolveModeratorName(UUID moderatorId) {
        if (moderatorId == null) return null;
        return moderatorNameCache.computeIfAbsent(moderatorId, id -> 
                userRepository.findById(id)
                        .map(user -> user.getFullName() != null && !user.getFullName().isBlank() 
                                ? user.getFullName() : user.getUsername())
                        .orElse("Unknown Moderator")
        );
    }

    private static final Set<String> PREMIUM_BYPASS_ROLES = Set.of(
            "ADMIN", "MODERATOR", "AUTHOR", "TRANSLATOR", "PROJECT_LEADER"
    );

    private static final String CHAPTER_DETAIL_CACHE_PREFIX = "chapter:detail:meta:";
    private static final String COMIC_CHAPTERS_LIST_CACHE_PREFIX = "comic:chapters:list:";

    private final com.sep.comiverse.repository.IChapterTranslationRepository chapterTranslationRepository;

    @Autowired
    public ChapterCrudPlugin(IChapterRepository repository,
                             PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                             RedisTemplate<String, Object> redisTemplate,
                             IUserRepository userRepository,
                             ChapterPremiumPolicyService chapterPremiumPolicyService,
                             PremiumPlanService premiumPlanService,
                             com.sep.comiverse.repository.IChapterTranslationRepository chapterTranslationRepository) {
        super(repository, pluginRegistry, ChapterEntity.class);
        this.chapterRepository = repository;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.chapterPremiumPolicyService = chapterPremiumPolicyService;
        this.premiumPlanService = premiumPlanService;
        this.chapterTranslationRepository = chapterTranslationRepository;
    }

    @Transactional(readOnly = true)
    public ChapterDTO getChapterDetail(UUID chapterId, UUID userId, String clientIp) {
        ChapterEntity chapter = chapterRepository
                .findByIdAndDeletedFalse(chapterId)
                .orElseThrow(() -> new CustomException(
                        404,
                        "Chapter not found",
                        HttpStatus.NOT_FOUND
                ));

        boolean isStaffOrPrivileged = false;
        if (userId != null) {
            isStaffOrPrivileged = (chapter.getComic() != null && userId.equals(chapter.getComic().getAuthorId()))
                    || userRepository.findByIdWithRole(userId)
                    .map(user -> {
                        String role = user.getRole() == null || user.getRole().getRoleName() == null
                                ? "READER"
                                : user.getRole().getRoleName().trim().toUpperCase(Locale.ROOT);
                        return PREMIUM_BYPASS_ROLES.contains(role);
                    })
                    .orElse(false);
        }

        // If not published, restrict access to privileged roles
        if (chapter.getModerationStatus() != ChapterStatus.PUBLISHED) {
            if (!isStaffOrPrivileged) {
                throw new CustomException(
                        404,
                        "Chapter not found or not published",
                        HttpStatus.NOT_FOUND
                );
            }
        }

        String cacheKey = CHAPTER_DETAIL_CACHE_PREFIX + chapterId;

        ChapterLiteDTO cacheDto = null;
        try {
            cacheDto = (ChapterLiteDTO) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Delete corrupt cache so it can be rebuilt.
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ignored) {
                // Redis is optional for chapter reads.
            }
        }

        if (cacheDto == null) {
            cacheDto = ChapterLiteDTO.builder()
                    .id(chapter.getId())
                    .comicId(chapter.getComic().getId())
                    .chapterNumber(chapter.getChapterNumber())
                    .title(chapter.getTitle())
                    .viewCount(chapter.getViewCount())
                    .isPremium(chapterPremiumPolicyService.isPremiumChapter(chapter.getChapterNumber()))
                    .createdAt(chapter.getCreatedAt())
                    .moderationStatus(chapter.getModerationStatus())
                    .approvedById(chapter.getApprovedById())
                    .approvedAt(chapter.getApprovedAt())
                    .build();

            try {
                redisTemplate.opsForValue().set(cacheKey, cacheDto, Duration.ofDays(3));
            } catch (Exception ignored) {
                // Redis is optional for chapter reads.
            }
        }

        List<String> images = chapter.getImages() == null
                ? Collections.emptyList()
                : chapter.getImages();
        boolean premiumRequired = chapterPremiumPolicyService.isPremiumChapter(cacheDto.getChapterNumber());
        boolean hasContentAccess = !premiumRequired || checkUserPremiumAccess(userId);

        ChapterDTO responseDto = ChapterDTO.builder()
                .id(cacheDto.getId())
                .comicId(cacheDto.getComicId())
                .chapterNumber(cacheDto.getChapterNumber())
                .title(cacheDto.getTitle())
                .viewCount(cacheDto.getViewCount())
                .isPremium(premiumRequired)
                .createdAt(cacheDto.getCreatedAt())
                .moderationStatus(chapter.getModerationStatus())
                .rejectionReason(chapter.getRejectionReason())
                .approvedAt(chapter.getApprovedAt())
                .approvedBy(resolveModeratorName(chapter.getApprovedById()))
                .rejectedBy(resolveModeratorName(chapter.getRejectedById()))
                .pageCount(chapter.getPageCount() != null ? chapter.getPageCount() : images.size())
                .build();

        responseDto.setImages(hasContentAccess ? images : Collections.emptyList());

        if (hasContentAccess && chapter.getModerationStatus() == ChapterStatus.PUBLISHED && !isStaffOrPrivileged) {
            try {
                trackAndIncrementView(responseDto.getComicId(), chapterId, userId, clientIp);
            } catch (Exception ignored) {
                // Redis view tracking must not block reading.
            }
        }

        try {
            Number rawChapterViews = (Number) redisTemplate.opsForHash()
                    .get(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString());
            if (rawChapterViews != null && responseDto.getViewCount() != null) {
                responseDto.setViewCount(responseDto.getViewCount() + rawChapterViews.intValue());
            }
        } catch (Exception ignored) {
            // Redis view counts are best effort.
        }

        return responseDto;
    }

    private void trackAndIncrementView(UUID comicId, UUID chapterId, UUID userId, String clientIp) {
        if (comicId == null || chapterId == null) return;
        try {
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
        } catch (Exception e) {
            // Log/ignore Redis tracking error if Redis is unreachable
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessChapterContent(UUID chapterId, UUID userId) {
        if (chapterId == null) {
            return false;
        }
        return chapterRepository
                .findByIdAndDeletedFalseAndModerationStatus(chapterId, ChapterStatus.PUBLISHED)
                .map(chapter -> !chapterPremiumPolicyService.isPremiumChapter(chapter.getChapterNumber())
                        || checkUserPremiumAccess(userId))
                .orElse(false);
    }

    private boolean checkUserPremiumAccess(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findByIdWithRole(userId)
                .map(user -> {
                    String role = user.getRole() == null || user.getRole().getRoleName() == null
                            ? "READER"
                            : user.getRole().getRoleName().trim().toUpperCase(Locale.ROOT);
                    return PREMIUM_BYPASS_ROLES.contains(role) || premiumPlanService.hasActivePremium(user);
                })
                .orElse(false);
    }

    private ChapterDTO maskPremiumImages(ChapterDTO dto) {
        return ChapterDTO.builder()
                .id(dto.getId())
                .comicId(dto.getComicId())
                .chapterNumber(dto.getChapterNumber())
                .title(dto.getTitle())
                .viewCount(dto.getViewCount())
                .isPremium(chapterPremiumPolicyService.isPremiumChapter(dto.getChapterNumber()))
                .createdAt(dto.getCreatedAt())
                .num(dto.getNum())
                .date(dto.getDate())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<ChapterLiteDTO> getChaptersByComicId(UUID comicId) {
        return getChaptersByComicId(comicId, false);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<ChapterLiteDTO> getChaptersByComicId(UUID comicId, boolean includeAll) {
        if (includeAll) {
            List<ChapterEntity> allChapters = chapterRepository.findAllByComic_IdAndDeletedFalse(comicId);
            return allChapters.stream().map(c -> ChapterLiteDTO.builder()
                    .id(c.getId())
                    .comicId(c.getComic() != null ? c.getComic().getId() : comicId)
                    .chapterNumber(c.getChapterNumber())
                    .title(c.getTitle())
                    .viewCount(c.getViewCount() != null ? c.getViewCount() : 0)
                    .isPremium(chapterPremiumPolicyService.isPremiumChapter(c.getChapterNumber()))
                    .createdAt(c.getCreatedAt())
                    .moderationStatus(c.getModerationStatus())
                    .rejectionReason(c.getRejectionReason())
                    .rejectedById(c.getRejectedById())
                    .approvedById(c.getApprovedById())
                    .approvedAt(c.getApprovedAt())
                    .pageCount(c.getPageCount() != null ? c.getPageCount() : (c.getImages() == null ? 0 : c.getImages().size()))
                    .build()
            ).sorted((a, b) -> toChapterSortNumber(a.getChapterNumber()).compareTo(toChapterSortNumber(b.getChapterNumber()))).toList();
        }

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

            if (cachedResults == null || cachedResults.isEmpty()) {
                return Collections.emptyList();
            }

            List<ChapterLiteDTO> listToCache = new java.util.ArrayList<>(cachedResults);
            try {
                redisTemplate.opsForValue().set(cacheKey, listToCache, Duration.ofHours(3));
            } catch (Exception e) {
                // Ignore Redis set errors
            }
        }
        applyLiveTranslatedLanguages(comicId, cachedResults);
        return cachedResults.stream().map(dto -> {
            ChapterLiteDTO copy = ChapterLiteDTO.builder()
                    .id(dto.getId())
                    .comicId(dto.getComicId())
                    .chapterNumber(dto.getChapterNumber())
                    .title(dto.getTitle())
                    .viewCount(dto.getViewCount())
                    .isPremium(chapterPremiumPolicyService.isPremiumChapter(dto.getChapterNumber()))
                    .createdAt(dto.getCreatedAt())
                    .moderationStatus(dto.getModerationStatus())
                    .rejectionReason(dto.getRejectionReason())
                    .rejectedById(dto.getRejectedById())
                    .approvedById(dto.getApprovedById())
                    .approvedAt(dto.getApprovedAt())
                    .pageCount(dto.getPageCount())
                    .translatedLanguages(dto.getTranslatedLanguages())
                    .build();

            try {
                Number rawChapterViews = (Number) redisTemplate.opsForHash().get(ViewSyncScheduler.CHAPTER_VIEW_HASH, copy.getId().toString());
                if (rawChapterViews != null && copy.getViewCount() != null) {
                    copy.setViewCount(copy.getViewCount() + rawChapterViews.intValue());
                }
            } catch (Exception e) {
                // Ignore Redis error if Redis is down
            }
            return copy;
        }).toList();
    }

    @Override
    @Transactional
    public ChapterDTO create(ChapterDTO dto) throws RuntimeException {
        ChapterDTO created = super.create(dto);
        if (created.getComicId() != null) {
            evictChaptersCache(created.getComicId());
        }
        return created;
    }

    @Override
    @Transactional
    public ChapterDTO update(UUID id, ChapterDTO dto) {
        ChapterDTO updated = super.update(id, dto);
        if (updated.getComicId() != null) {
            evictChaptersCache(updated.getComicId());
            evictChapterDetailCache(updated.getId());
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ChapterEntity chapter = chapterRepository.findById(id).orElse(null);
        super.delete(id);
        if (chapter != null && chapter.getComic() != null) {
            evictChaptersCache(chapter.getComic().getId());
            evictChapterDetailCache(id);
        }
    }

    private void applyLiveTranslatedLanguages(UUID comicId, List<ChapterLiteDTO> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return;
        }
        java.util.List<Object[]> langMapping = chapterTranslationRepository.findLanguageCodesByChapterForComic(comicId);
        if (langMapping == null) {
            langMapping = java.util.Collections.emptyList();
        }
        java.util.Map<UUID, java.util.List<String>> chapterLangs = new java.util.HashMap<>();
        for (Object[] row : langMapping) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            UUID chapId = (UUID) row[0];
            String langCode = com.sep.comiverse.util.LanguageCodes.normalize(String.valueOf(row[1]));
            java.util.List<String> langs = chapterLangs.computeIfAbsent(chapId, k -> new java.util.ArrayList<>());
            if (!langs.contains(langCode)) {
                langs.add(langCode);
            }
        }
        for (ChapterLiteDTO dto : chapters) {
            dto.setTranslatedLanguages(chapterLangs.getOrDefault(dto.getId(), new java.util.ArrayList<>()));
        }
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

    private java.math.BigDecimal toChapterSortNumber(String chapterNumber) {
        if (chapterNumber == null || chapterNumber.isBlank()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(chapterNumber.replace(',', '.'));
        } catch (Exception ex) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
