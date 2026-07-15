package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ComicExploreRequestDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.service.scheduler.LeaderboardScheduler;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import com.sep.comiverse.repository.IGenreRepository;
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
    private final LeaderboardScheduler leaderboardScheduler;

    private static final String COMIC_CACHE_PREFIX = "comic:detail:";
    private final IGenreRepository genreRepository;

    @Autowired
    public ComicCrudPlugin(IComicRepository repository,
                           IGenreRepository genreRepository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                           RedisTemplate<String, Object> redisTemplate,
                           @org.springframework.context.annotation.Lazy LeaderboardScheduler leaderboardScheduler) {
        super(repository, pluginRegistry, ComicEntity.class);
        this.comicRepository = repository;
        this.genreRepository = genreRepository;
        this.redisTemplate = redisTemplate;
        this.leaderboardScheduler = leaderboardScheduler;
    }

    public ComicCrudPlugin(IComicRepository repository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                           RedisTemplate<String, Object> redisTemplate,
                           LeaderboardScheduler leaderboardScheduler) {
        this(repository, null, pluginRegistry, redisTemplate, leaderboardScheduler);
    }

    @Override
    @Transactional
    public ComicDTO update(UUID id, ComicDTO dto) throws RuntimeException {
        ComicEntity existing = comicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comic not found"));

        if (dto.getTitle() != null) {
            existing.setTitle(dto.getTitle());
        }
        if (dto.getSlug() != null) {
            existing.setSlug(dto.getSlug());
        }
        if (dto.getSummary() != null) {
            existing.setSummary(dto.getSummary());
        }
        if (dto.getAuthorId() != null) {
            existing.setAuthorId(dto.getAuthorId());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        if (dto.getModerationStatus() != null) {
            existing.setModerationStatus(dto.getModerationStatus());
        }
        if (dto.getCover() != null) {
            existing.setCover(dto.getCover());
        }
        if (dto.getThumbnail() != null) {
            existing.setThumbnail(dto.getThumbnail());
        }
        if (dto.getViewCount() != null) {
            existing.setViewCount(dto.getViewCount());
        }
        if (dto.getLikeCount() != null) {
            existing.setLikeCount(dto.getLikeCount());
        }
        if (dto.getSaveCount() != null) {
            existing.setSaveCount(dto.getSaveCount());
        }
        if (dto.getRatingAverage() != null) {
            existing.setRatingAverage(dto.getRatingAverage());
        }
        if (dto.getRatingCount() != null) {
            existing.setRatingCount(dto.getRatingCount());
        }
        if (dto.getLatestChapterNumber() != null) {
            existing.setLatestChapterNumber(dto.getLatestChapterNumber());
        }
        if (dto.getChapterCount() != null) {
            existing.setChapterCount(dto.getChapterCount());
        }
        if (dto.getLastChapterUpdatedAt() != null) {
            existing.setLastChapterUpdatedAt(dto.getLastChapterUpdatedAt());
        }

        // Update genres relation and genre_ids list property
        if (dto.getGenreIds() != null) {
            List<GenreEntity> genreEntities = genreRepository.findAllById(dto.getGenreIds());
            existing.setGenres(new java.util.HashSet<>(genreEntities));
            List<UUID> validGenreIds = genreEntities.stream()
                    .map(GenreEntity::getId)
                    .toList();
            existing.setGenreIds(validGenreIds);
        }

        ComicEntity saved = comicRepository.save(existing);
        return plugin.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ComicDTO> listPublishedComics() {
        return comicRepository.findAllByDeletedFalseAndModerationStatusWithGenres(ComicModerationStatus.PUBLISHED)
                .stream()
                .map(plugin::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ComicDTO> listPublishedComics(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();
        return comicRepository.findPublishedComics(ComicModerationStatus.PUBLISHED, paginationDTO.getSearch(), pageable)
                .map(plugin::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ComicDTO> getTopViews(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();
        return comicRepository
                .findByDeletedFalseAndModerationStatusOrderByViewCountDesc(
                        ComicModerationStatus.PUBLISHED,
                        pageable
                )
                .map(plugin::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ComicDTO> getComicsByLatestChapters(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();
        return comicRepository
                .findComicsByLatestChapters(ComicModerationStatus.PUBLISHED, pageable)
                .map(plugin::toDto);
    }

    @Transactional(readOnly = true)
    public ComicDTO getComicDetail(UUID comicId) {
        String cacheKey = COMIC_CACHE_PREFIX + comicId.toString();
        String comicIdStr = comicId.toString();
        ComicDTO dto = null;
        try {
            dto = (ComicDTO) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Delete corrupt cache so it can be rebuilt
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // Ignore
            }
        }

        if (dto == null) {
            ComicEntity entity = comicRepository.findById(comicId)
                    .orElseThrow(() -> new RuntimeException("Comic not found"));

            dto = plugin.toDto(entity);

            try {
                redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofHours(24));
            } catch (Exception e) {
                // Ignore Redis set errors
            }
        }

        //increase view
        try {
            Number rawViews = (Number) redisTemplate.opsForHash().get(ViewSyncScheduler.COMIC_VIEW_HASH, comicIdStr);
            if (rawViews != null) {
                dto.setViewCount(dto.getViewCount() + rawViews.intValue());
            }
        } catch (Exception e) {
            // Ignore Redis hash errors
        }

        //increase like
        try {
            Number rawLikes = (Number) redisTemplate.opsForHash().get(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicIdStr);
            if (rawLikes != null) {
                dto.setLikeCount(dto.getLikeCount() + rawLikes.intValue());
            }
        } catch (Exception e) {
            // Ignore Redis hash errors
        }

        //increase save
        try {
            Number rawSaves = (Number) redisTemplate.opsForHash().get(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr);
            if (rawSaves != null) {
                dto.setSaveCount(dto.getSaveCount() + rawSaves.intValue());
            }
        } catch (Exception e) {
            // Ignore Redis hash errors
        }

        return dto;
    }

    @SuppressWarnings("unchecked")
    public List<ComicDTO> getCachedLeaderboard(String timeframe) {
        String cacheKey = LeaderboardScheduler.LEADERBOARD_CACHE_KEY_PREFIX + timeframe;
        List<ComicDTO> ranking = null;
        try {
            ranking = (List<ComicDTO>) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Delete corrupt cache so it can be rebuilt
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                // Ignore
            }
        }

        if (ranking == null) {
            try {
                leaderboardScheduler.computeLeaderboards();
                ranking = (List<ComicDTO>) redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                // If scheduler or redis fails, fallback gracefully
            }
        }

        return ranking != null ? ranking : java.util.Collections.emptyList();
    }

    public void evictComicCache(UUID comicId) {
        String cacheKey = COMIC_CACHE_PREFIX + comicId.toString();
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // Ignore/log error
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

        List<ComicDTO> dtoList = resultEntities.stream().map(plugin::toDto).toList();

        String nextCursor = null;
        UUID nextReferenceId = null;

        if (!dtoList.isEmpty() && hasMore) {
            ComicEntity lastEntity = resultEntities.getLast();
            nextReferenceId = lastEntity.getId();
            nextCursor = isTimeField ? getTimeProperty(lastEntity, sortProperty) : getNumberProperty(lastEntity, sortProperty);
        }

        return new CursorResponseDTO<>(dtoList, nextCursor, nextReferenceId, hasMore);
    }

    public List<ComicDTO> listPublishedComicsWithoutGenres() {
        return comicRepository.findAllByDeletedFalseAndModerationStatus(ComicModerationStatus.PUBLISHED)
                .stream()
                .map(plugin::toDto)
                .toList();
    }

    public Page<ComicDTO> listPublishedComicsWithoutGenres(PaginationSearchDTO paginationDTO) {
        Pageable pageable = paginationDTO.toPageRequest();
        return comicRepository.findPublishedComics(ComicModerationStatus.PUBLISHED, paginationDTO.getSearch(), pageable)
                .map(plugin::toDto);
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
