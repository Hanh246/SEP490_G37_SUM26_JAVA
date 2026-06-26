package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IProjectTeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProjectTeamCrudPlugin extends AbstractCrudPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    @Autowired
    public ProjectTeamCrudPlugin(IProjectTeamRepository repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry) {
        super(repository, pluginRegistry, ProjectTeamEntity.class);
    }
}
