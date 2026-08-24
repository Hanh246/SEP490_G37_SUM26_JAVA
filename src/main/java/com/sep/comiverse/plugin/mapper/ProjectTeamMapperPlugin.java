package com.sep.comiverse.plugin.mapper;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.AbstractMapperPlugin;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.util.ProjectTeamStatuses;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProjectTeamMapperPlugin extends AbstractMapperPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID> {

    private final ITeamTaskRepository teamTaskRepository;

    @Autowired
    public ProjectTeamMapperPlugin(ModelMapper modelMapper, ITeamTaskRepository teamTaskRepository) {
        super(ProjectTeamEntity.class, ProjectTeamDTO.class, UUID.class, modelMapper);
        this.teamTaskRepository = teamTaskRepository;
    }

    @Override
    public ProjectTeamDTO toDto(ProjectTeamEntity model) {
        if (model == null) return null;
        ProjectTeamDTO dto = super.toDto(model);
        dto.setComicTitle(model.getComicName());
        dto.setNotes(model.getNotes());
        
        long count = teamTaskRepository.countIncompleteTasksByTeam(model.getId());
        dto.setInProgressTasksCount(count);

        long completedCount = teamTaskRepository.countCompletedTasksByTeam(model.getId());
        dto.setCompletedTasksCount(completedCount);
        
        return dto;
    }

    @Override
    protected void performCustomUpdate(ProjectTeamEntity existingModel, ProjectTeamDTO dto) {
        if (dto == null || existingModel == null) {
            return;
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            existingModel.setStatus(ProjectTeamStatuses.normalize(dto.getStatus()));
        }
        if (ProjectTeamStatuses.isCompleted(existingModel.getStatus())) {
            existingModel.setIsRecruiting(false);
        }
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
