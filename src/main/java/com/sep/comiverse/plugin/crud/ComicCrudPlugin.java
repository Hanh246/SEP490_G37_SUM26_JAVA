package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IComicRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
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
}
