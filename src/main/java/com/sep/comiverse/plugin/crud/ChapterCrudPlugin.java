package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ChapterCrudPlugin
        extends AbstractCrudPlugin<ChapterEntity, ChapterDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ChapterCrudPlugin(IChapterRepository repository,
                             PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry){
        super(repository, pluginRegistry, ChapterEntity.class);
    }
}
