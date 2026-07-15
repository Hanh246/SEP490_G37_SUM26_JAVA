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
    private com.sep.comiverse.service.AuditLogService auditLogService;

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
        ProjectTeamDTO created = super.create(dto);
        auditLogService.log("PROJECT_TEAMS", "Created project team: " + created.getTitle() + " for comic: " + created.getComicName());
        return created;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProjectTeamDTO update(UUID id, ProjectTeamDTO dto) throws RuntimeException {
        var existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            if (dto.getLeaderName() == null) {
                dto.setLeaderName(existing.getLeaderName());
            }
            if (dto.getLeaderInitials() == null) {
                dto.setLeaderInitials(existing.getLeaderInitials());
            }
            if (dto.getMembersCount() == null) {
                dto.setMembersCount(existing.getMembersCount());
            }
            if (dto.getChaptersCount() == null) {
                dto.setChaptersCount(existing.getChaptersCount());
            }
            if (dto.getProgress() == null) {
                dto.setProgress(existing.getProgress());
            }
            if (dto.getAssignedToMe() == null) {
                dto.setAssignedToMe(existing.getAssignedToMe());
            }
            if (dto.getCover() == null) {
                dto.setCover(existing.getCover());
            }
            if (dto.getDeadline() == null) {
                dto.setDeadline(existing.getDeadline());
            }
            if (dto.getSourceLang() == null) {
                dto.setSourceLang(existing.getSourceLang());
            }
            if (dto.getTargetLang() == null) {
                dto.setTargetLang(existing.getTargetLang());
            }
            if (dto.getPriority() == null) {
                dto.setPriority(existing.getPriority());
            }
        }
        return super.update(id, dto);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void delete(UUID id) throws RuntimeException {
        String teamTitle = "Unknown Team";
        try {
            var existing = repository.findById(id).orElse(null);
            if (existing != null) {
                teamTitle = existing.getTitle();
            }
        } catch (Exception e) {}
        
        super.delete(id);
        auditLogService.log("PROJECT_TEAMS", "Removed project team: " + teamTitle);
    }
}
