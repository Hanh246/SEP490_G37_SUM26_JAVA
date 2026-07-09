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

import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import java.util.List;

@Component
public class ProjectTeamCrudPlugin extends AbstractCrudPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    private final ISubmissionRepository submissionRepository;
    private final ITeamTaskRepository teamTaskRepository;

    @Autowired
    public ProjectTeamCrudPlugin(IProjectTeamRepository repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                                 ISubmissionRepository submissionRepository,
                                 ITeamTaskRepository teamTaskRepository) {
        super(repository, pluginRegistry, ProjectTeamEntity.class);
        this.submissionRepository = submissionRepository;
        this.teamTaskRepository = teamTaskRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProjectTeamDTO create(ProjectTeamDTO dto) throws RuntimeException {
        // Create project team using base CRUD logic
        ProjectTeamDTO createdDto = super.create(dto);

        // Find the comic name
        String comicName = createdDto.getComicName();
        if (comicName != null && !comicName.trim().isEmpty()) {
            // Find all approved submissions of type "author" for this comic
            List<SubmissionEntity> approvedSubmissions = submissionRepository.findAll().stream()
                    .filter(s -> "author".equalsIgnoreCase(s.getQueueType())
                            && "approved".equalsIgnoreCase(s.getStatus())
                            && s.getTitle() != null
                            && s.getTitle().equalsIgnoreCase(comicName))
                    .toList();

            // Create a task in the team's backlog for each approved chapter
            for (SubmissionEntity sub : approvedSubmissions) {
                String chapterTitle = sub.getChapter();
                if (chapterTitle == null || chapterTitle.trim().isEmpty()) {
                    chapterTitle = "Chapter 1";
                }
                String taskTitle = chapterTitle.contains("Translation") ? chapterTitle : chapterTitle + " - Translation";

                TeamTaskEntity task = TeamTaskEntity.builder()
                        .projectTeamId(createdDto.getId())
                        .title(taskTitle)
                        .columnName("backlog")
                        .progress(0)
                        .assignees("")
                        .build();
                teamTaskRepository.save(task);
            }
        }

        return createdDto;
    }
}
