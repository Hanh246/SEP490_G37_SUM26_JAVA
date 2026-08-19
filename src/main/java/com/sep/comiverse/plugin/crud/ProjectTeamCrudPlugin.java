package com.sep.comiverse.plugin.crud;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.plugin.AbstractCrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import com.sep.comiverse.repository.IProjectTeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import java.util.List;

@Component
public class ProjectTeamCrudPlugin extends AbstractCrudPlugin<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    private final ISubmissionRepository submissionRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final IUserRepository userRepository;

    @Autowired
    private com.sep.comiverse.service.AuditLogService auditLogService;

    @Autowired
    private com.sep.comiverse.service.NotificationService notificationService;

    @Autowired
    public ProjectTeamCrudPlugin(IProjectTeamRepository repository,
                                 PluginRegistry<IMapperPlugin, Class<?>> pluginRegistry,
                                 ISubmissionRepository submissionRepository,
                                 ITeamTaskRepository teamTaskRepository,
                                 IUserRepository userRepository) {
        super(repository, pluginRegistry, ProjectTeamEntity.class);
        this.submissionRepository = submissionRepository;
        this.teamTaskRepository = teamTaskRepository;
        this.userRepository = userRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProjectTeamDTO create(ProjectTeamDTO dto) throws RuntimeException {
        if (dto.getLeaderId() == null || dto.getLeaderName() == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.sep.comiverse.security.UserPrincipal) {
                com.sep.comiverse.security.UserPrincipal principal = (com.sep.comiverse.security.UserPrincipal) auth.getPrincipal();
                if (dto.getLeaderId() == null) dto.setLeaderId(principal.getId());
                if (dto.getLeaderName() == null) dto.setLeaderName(principal.getFullName() != null ? principal.getFullName() : principal.getUsername());
            }
        }
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus("ongoing");
        }
        ProjectTeamDTO created = super.create(dto);
        auditLogService.log("PROJECT_TEAMS", "Created project team: " + created.getTitle() + " for comic: " + created.getComicName());
        notifyLeaderAboutAssignment(created, "You were assigned as project leader");
        return created;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProjectTeamDTO update(UUID id, ProjectTeamDTO dto) throws RuntimeException {
        var existingOpt = repository.findById(id);
        UUID previousLeaderId = null;
        String previousComicName = null;
        String previousTargetLang = null;
        String previousStatus = null;
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            previousLeaderId = existing.getLeaderId();
            previousComicName = existing.getComicName();
            previousTargetLang = existing.getTargetLang();
            previousStatus = existing.getStatus();
            if (dto.getLeaderName() == null) {
                dto.setLeaderName(existing.getLeaderName());
            }
            if (dto.getLeaderId() == null) {
                dto.setLeaderId(existing.getLeaderId());
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
        if (dto.getLeaderId() != null && !dto.getLeaderId().equals(previousLeaderId)) {
            var newLeaderOpt = userRepository.findById(dto.getLeaderId());
            if (newLeaderOpt.isPresent()) {
                var newLeader = newLeaderOpt.get();
                if (newLeader.getRole() == null || !"PROJECT_LEADER".equalsIgnoreCase(newLeader.getRole().getRoleName())) {
                    throw new com.sep.comiverse.exception.CustomException(400, "Assigned user must have the PROJECT_LEADER role.", org.springframework.http.HttpStatus.BAD_REQUEST);
                }
            }
            if (previousLeaderId != null) {
                long incompleteCount = teamTaskRepository.countIncompleteTasksByTeam(id);
                if (incompleteCount > 0) {
                    throw new com.sep.comiverse.exception.CustomException(400, "Cannot reassign leader. There are " + incompleteCount + " incomplete tasks in this team. Please complete or unassign them first.", org.springframework.http.HttpStatus.BAD_REQUEST);
                }
            }
        }

        ProjectTeamDTO updated = super.update(id, dto);
        boolean leaderChanged = updated.getLeaderId() != null && !updated.getLeaderId().equals(previousLeaderId);
        boolean assignmentChanged = !java.util.Objects.equals(previousComicName, updated.getComicName())
                || !java.util.Objects.equals(previousTargetLang, updated.getTargetLang())
                || !java.util.Objects.equals(previousStatus, updated.getStatus());
        if (leaderChanged) {
            notifyLeaderAboutAssignment(updated, "You were assigned as project leader");
        } else if (updated.getLeaderId() != null && assignmentChanged) {
            notifyLeaderAboutAssignment(updated, "New project update");
        }
        return updated;
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

    private void notifyLeaderAboutAssignment(ProjectTeamDTO team, String title) {
        if (team == null || team.getLeaderId() == null) {
            return;
        }
        String projectName = team.getComicName() == null || team.getComicName().isBlank()
                ? team.getTitle()
                : team.getComicName();
        String language = team.getTargetLang() == null || team.getTargetLang().isBlank()
                ? "the selected language"
                : team.getTargetLang();
        notificationService.notifyUser(
                team.getLeaderId(),
                title,
                "You are responsible for " + projectName + " (" + language + ").",
                "UPDATE",
                NotificationPreferenceKey.TEAM_UPDATES
        );
    }
}
