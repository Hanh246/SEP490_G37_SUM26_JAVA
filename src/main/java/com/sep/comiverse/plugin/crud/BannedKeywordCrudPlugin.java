package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.BannedKeywordDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.BannedKeywordEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IBannedKeywordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BannedKeywordCrudPlugin extends AbstractCrudPlugin<BannedKeywordEntity, BannedKeywordDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public BannedKeywordCrudPlugin(IBannedKeywordRepository repository,
                                   PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, BannedKeywordEntity.class);
    }
}
