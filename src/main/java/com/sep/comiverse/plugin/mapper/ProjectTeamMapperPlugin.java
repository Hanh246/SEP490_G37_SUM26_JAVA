package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProjectTeamMapperPlugin extends AbstractMapperPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID> {

    @Autowired
    public ProjectTeamMapperPlugin(ModelMapper modelMapper) {
        super(ProjectTeamEntity.class, ProjectTeamDTO.class, UUID.class, modelMapper);
    }

    @Override
    public ProjectTeamDTO toDto(ProjectTeamEntity model) {
        if (model == null) return null;
        ProjectTeamDTO dto = super.toDto(model);
        dto.setComicTitle(model.getComicName());
        dto.setNotes(model.getNotes());
            return dto;
    }

    @Override
    protected void configureModelMapper() {
        super.configureModelMapper();
    }

    @Override
    public List<String> getSearchableFieldNames() {
        return List.of("title", "comicName", "leaderName");
    }
}
