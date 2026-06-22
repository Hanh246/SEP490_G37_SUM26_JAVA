package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IGenreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GenreCrudPlugin
        extends AbstractCrudPlugin<GenreEntity, GenreDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public GenreCrudPlugin(IGenreRepository repository,
                           PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry){
        super(repository, pluginRegistry, GenreEntity.class);
    }
}
