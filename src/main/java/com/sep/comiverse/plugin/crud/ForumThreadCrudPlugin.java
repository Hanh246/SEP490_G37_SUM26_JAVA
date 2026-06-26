package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IForumThreadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ForumThreadCrudPlugin extends AbstractCrudPlugin<ForumThreadEntity, ForumThreadDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ForumThreadCrudPlugin(IForumThreadRepository repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ForumThreadEntity.class);
    }
}
