package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ComicDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IComicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ComicCrudPlugin extends AbstractCrudPlugin<ComicEntity, ComicDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ComicCrudPlugin(IComicRepository repository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ComicEntity.class);
    }
}
