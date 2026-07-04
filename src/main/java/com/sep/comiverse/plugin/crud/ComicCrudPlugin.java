package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.specification.ComicSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class ComicCrudPlugin extends AbstractCrudPlugin<ComicEntity, ComicDTO, UUID, PaginationSearchDTO> {

    private final IComicRepository comicRepository;
    private final RedisTemplate<String, Object> redisTemplate;


    private static final String COMIC_CACHE_PREFIX = "comic:detail:";

    // Redis Sets to track unique user interactions and prevent spamming
    private static final String COMIC_LIKE_USERS_SET_PREFIX = "comic:like:users:";
    private static final String COMIC_SAVE_USERS_SET_PREFIX = "comic:save:users:";

    // Redis Hashes for atomic multi-user counters
    private static final String COMIC_VIEW_HASH = "comic:view:counter";
    private static final String COMIC_LIKE_HASH = "comic:like:counter";
    private static final String COMIC_SAVE_HASH = "comic:save:counter";

    @Autowired
    public ComicCrudPlugin(IComicRepository repository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                           RedisTemplate<String, Object> redisTemplate) {
        super(repository, pluginRegistry, ComicEntity.class);
        this.comicRepository = repository;
        this.redisTemplate = redisTemplate;
    }

    public Page<ComicDTO> getTopViews(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();

        Page<ComicEntity> comicPage = comicRepository.findByOrderByViewCountDesc(pageable);

        return comicPage.map(plugin::toDto);
    }

    public Page<ComicDTO> getComicsByLatestChapters(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();

        Page<ComicEntity> comicPage = comicRepository.findComicsByLatestChapters(pageable);

        return comicPage.map(plugin::toDto);
    }

    public boolean isComicLikedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;

        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_LIKE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        // 1. Check inside the temporary Redis buffer first
        Boolean isLikedInRedis = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);
        if (Boolean.TRUE.equals(isLikedInRedis)) {
            return true;
        }

        // 2. If not found in Redis, check the database (PostgreSQL) using a lightweight EXISTS query
        // return userLikeRepository.existsByComicIdAndUserId(comicId, userId);
        return false; // Temporary default return, replace with your repository call
    }

    /**
     * Checks if a user has bookmarked/saved a comic by combining Redis temporary buffer and DB.
     */
    public boolean isComicSavedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;

        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        // 1. Check inside the temporary Redis buffer first
        Boolean isSavedInRedis = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);
        if (Boolean.TRUE.equals(isSavedInRedis)) {
            return true;
        }

        // 2. If not found in Redis, check the database (PostgreSQL)
        // return userSaveRepository.existsByComicIdAndUserId(comicId, userId);
        return false; // Temporary default return, replace with your repository call
    }

    @Transactional(readOnly = true)
    public ComicDTO getComicDetail(UUID comicId) {
        String cacheKey = COMIC_CACHE_PREFIX + comicId.toString();
        String comicIdStr = comicId.toString();

        ComicDTO dto = (ComicDTO) redisTemplate.opsForValue().get(cacheKey);

        if (dto == null) {
            ComicEntity entity = comicRepository.findById(comicId)
                    .orElseThrow(() -> new RuntimeException("Comic not found"));

            dto = plugin.toDto(entity);

            redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofHours(24));
        }

        //increase view
        Integer redisViews = (Integer) redisTemplate.opsForHash().get(COMIC_VIEW_HASH, comicIdStr);
        if (redisViews != null) {
            dto.setViewCount(dto.getViewCount() + redisViews);
        }
        //increase like
        Integer redisLikes = (Integer) redisTemplate.opsForHash().get(COMIC_LIKE_HASH, comicIdStr);
        if (redisLikes != null) {
            dto.setLikeCount(dto.getLikeCount() + redisLikes);
        }
        //increase save
        Integer redisSaves = (Integer) redisTemplate.opsForHash().get(COMIC_SAVE_HASH, comicIdStr);
        if (redisSaves != null) {
            dto.setSaveCount(dto.getSaveCount() + redisSaves);
        }

        return dto;
    }

    public boolean toggleLikeComic(UUID comicId, UUID userId) {
        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_LIKE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        Boolean isLiked = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);

        if (Boolean.FALSE.equals(isLiked)) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, 1);

            // TODO: Push message (comicId, userId, "LIKE") to Message Queue (Kafka/RabbitMQ) for deferred DB synchronization
            return true;
        } else {
            redisTemplate.opsForSet().remove(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, -1);

            // TODO: Push message (comicId, userId, "UNLIKE") to Message Queue for deferred DB synchronization
            return false;
        }
    }

    public boolean toggleSaveComic(UUID comicId, UUID userId) {
        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        Boolean isSaved = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);

        if (Boolean.FALSE.equals(isSaved)) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, 1);

            // TODO: Push message (comicId, userId, "SAVE") to Message Queue for deferred DB synchronization
            return true;
        } else {
            redisTemplate.opsForSet().remove(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, -1);

            // TODO: Push message (comicId, userId, "UNSAVE") to Message Queue for deferred DB synchronization
            return false;
        }
    }

    public CursorResponseDTO<ComicDTO> getExploreComicsCursor(ComicExploreRequestDTO request) {
        String sortProperty = "createdAt";
        boolean isTimeField = true;

        String sortByStr = request.getSortBy() != null ? request.getSortBy() : "Default";
        switch (sortByStr) {
            case "Recently Added":
                sortProperty = "createdAt";
                break;
            case "Recently Updated":
                sortProperty = "lastChapterUpdatedAt";
                break;
            case "Total Views":
                sortProperty = "viewCount";
                isTimeField = false;
                break;
            case "Most Liked":
                sortProperty = "likeCount";
                isTimeField = false;
                break;
            case "Most Followed":
                sortProperty = "saveCount";
                isTimeField = false;
                break;
        }

        Sort sort = Sort.by(Sort.Direction.DESC, sortProperty).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(0, request.getSize() + 1, sort);

        Specification<ComicEntity> spec = ComicSpecification.filterCursorComics(request, sortProperty, isTimeField);
        List<ComicEntity> entities = comicRepository.findAll(spec, pageable).getContent();

        boolean hasMore = entities.size() > request.getSize();
        List<ComicEntity> resultEntities = hasMore ? entities.subList(0, request.getSize()) : entities;

        List<ComicDTO> dtoList = resultEntities.stream().map(entity -> {
            ComicDTO dto = new ComicDTO();
            dto.setId(entity.getId());
            dto.setTitle(entity.getTitle());
            dto.setStatus(entity.getStatus());
            dto.setCover(entity.getCover());
            dto.setViewCount(entity.getViewCount());
            return dto;
        }).toList();

        String nextCursor = null;
        UUID nextReferenceId = null;

        if (!dtoList.isEmpty() && hasMore) {
            ComicEntity lastEntity = resultEntities.getLast();
            nextReferenceId = lastEntity.getId();
            nextCursor = isTimeField ? getTimeProperty(lastEntity, sortProperty) : getNumberProperty(lastEntity, sortProperty);
        }

        return new CursorResponseDTO<>(dtoList, nextCursor, nextReferenceId, hasMore);
    }

    private String getTimeProperty(ComicEntity entity, String property) {
        if ("lastChapterUpdatedAt".equals(property)) {
            return entity.getLastChapterUpdatedAt() != null ? entity.getLastChapterUpdatedAt().toString() : entity.getCreatedAt().toString();
        }
        return entity.getCreatedAt().toString();
    }

    private String getNumberProperty(ComicEntity entity, String property) {
        if ("viewCount".equals(property)) return entity.getViewCount().toString();
        if ("likeCount".equals(property)) return entity.getLikeCount().toString();
        if ("saveCount".equals(property)) return entity.getSaveCount().toString();
        return entity.getCreatedAt().toString();
    }
}
