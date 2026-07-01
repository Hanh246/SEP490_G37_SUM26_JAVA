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
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ComicCrudPlugin extends AbstractCrudPlugin<ComicEntity, ComicDTO, UUID, PaginationSearchDTO> {

    private final IComicRepository comicRepository;

    @Autowired
    public ComicCrudPlugin(IComicRepository repository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ComicEntity.class);
        this.comicRepository = repository;
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
