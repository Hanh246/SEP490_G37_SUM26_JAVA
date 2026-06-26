package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ChatFlagDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ChatFlagEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IChatFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ChatFlagCrudPlugin extends AbstractCrudPlugin<ChatFlagEntity, ChatFlagDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ChatFlagCrudPlugin(IChatFlagRepository repository,
                              PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ChatFlagEntity.class);
    }
}
