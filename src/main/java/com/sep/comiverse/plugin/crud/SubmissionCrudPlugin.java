package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.ISubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class SubmissionCrudPlugin extends AbstractCrudPlugin<SubmissionEntity, SubmissionDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public SubmissionCrudPlugin(ISubmissionRepository repository,
                                PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, SubmissionEntity.class);
    }
}
