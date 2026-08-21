package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.plugin.crud.ProjectTeamCrudPlugin;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import com.sep.comiverse.dto.response.TeamStatsDTO;
import com.sep.comiverse.dto.response.TranslatorDashboardDTO;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.ComicEntity;

@RestController
@RequestMapping("/project-teams")
public class ProjectTeamController extends BaseController<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    private final IProjectTeamRepository iProjectTeamRepository;

    @Autowired
    private ITeamTaskRepository teamTaskRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    public ProjectTeamController(ProjectTeamCrudPlugin crud, IProjectTeamRepository iProjectTeamRepository) {
        super(crud, ProjectTeamEntity.class);
        this.iProjectTeamRepository = iProjectTeamRepository;
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ProjectTeamDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ProjectTeamDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @GetMapping("/myprojects")
    public ResponseEntity<List<ProjectTeamDTO>> listMyProjects() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        UUID userId = principal.getId();
        String fullName = principal.getFullName() != null ? principal.getFullName().trim() : "";
        String username = principal.getUsername() != null ? principal.getUsername().trim() : "";

        List<ProjectTeamEntity> teams = iProjectTeamRepository.findMyTeams(userId, fullName, username);
        List<ProjectTeamDTO> result = teams.stream().map(this::toDto).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/myprojects/dashboard")
    public ResponseEntity<TranslatorDashboardDTO> getTranslatorDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        UUID userId = principal.getId();
        String fullName = principal.getFullName() != null ? principal.getFullName().trim() : "";
        String username = principal.getUsername() != null ? principal.getUsername().trim() : "";

        List<ProjectTeamEntity> teams = iProjectTeamRepository.findMyTeams(userId, fullName, username);
        List<ProjectTeamDTO> projects = teams.stream().map(this::toDto).collect(Collectors.toList());

        Map<UUID, TeamStatsDTO> teamStats = new HashMap<>();
        List<TeamTaskEntity> activeTasks = new ArrayList<>();

        for (ProjectTeamEntity team : teams) {
            List<TeamTaskEntity> tasks = teamTaskRepository.findByProjectTeamId(team.getId());
            long totalChapters = 0;
            
            String comicName = team.getComicName();
            if (comicName != null && !comicName.isBlank()) {
                List<ComicEntity> comics = comicRepository.findAllByTitle(comicName);
                if (comics.isEmpty()) {
                    comics = comicRepository.findAllByTitleIgnoreCase(comicName);
                }
                if (!comics.isEmpty()) {
                    totalChapters = chapterRepository.countByComic_IdAndDeletedFalse(comics.get(0).getId());
                }
            }

            long totalTasks = tasks.size();
            long backlog = 0, inProgress = 0, review = 0, done = 0;

            for (TeamTaskEntity task : tasks) {
                String col = task.getStatus() != null ? task.getStatus().toLowerCase() : "";
                if (col.contains("done") || col.contains("completed") || col.contains("published")) {
                    done++;
                } else if (col.contains("progress") || col.contains("doing")) {
                    inProgress++;
                } else if (col.contains("review")) {
                    review++;
                } else {
                    backlog++;
                }

                if (!col.contains("done") && !col.contains("completed") && !col.contains("published")
                        && !col.contains("superseded")
                        && userId.equals(task.getAssigneeId())
                        && task.getCompletedAt() == null) {
                    activeTasks.add(task);
                }
            }

            teamStats.put(team.getId(), TeamStatsDTO.builder()
                    .totalTasks(totalTasks)
                    .backlog(backlog)
                    .inProgress(inProgress)
                    .review(review)
                    .done(done)
                    .totalChapters(totalChapters)
                    .build());
        }

        TranslatorDashboardDTO response = TranslatorDashboardDTO.builder()
                .projects(projects)
                .teamStats(teamStats)
                .activeTasks(activeTasks)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/myprojects/page")
    public ResponseEntity<BaseResponse<org.springframework.data.domain.Page<ProjectTeamDTO>>> listMyProjectsPage(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "4") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        UUID userId = principal.getId();
        String fullName = principal.getFullName() != null ? principal.getFullName().trim() : "";
        String username = principal.getUsername() != null ? principal.getUsername().trim() : "";

        String searchQuery = (search != null && !search.trim().isEmpty()) ? search.trim() : "";

        org.springframework.data.domain.Page<ProjectTeamEntity> teams = iProjectTeamRepository.findMyTeamsPaginated(
                userId,
                fullName,
                username,
                searchQuery,
                org.springframework.data.domain.PageRequest.of(page - 1, size)
        );
        return ResponseEntity.ok(BaseResponse.<org.springframework.data.domain.Page<ProjectTeamDTO>>builder()
                .success(true)
                .data(teams.map(this::toDto))
                .build());
    }

    private ProjectTeamDTO toDto(ProjectTeamEntity e) {
        ProjectTeamDTO dto = new ProjectTeamDTO();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setComicName(e.getComicName());
        dto.setStatus(e.getStatus());
        dto.setMembersCount(e.getMembersCount());
        dto.setChaptersCount(e.getChaptersCount());
        dto.setProgress(e.getProgress());
        dto.setLeaderName(e.getLeaderName());
        dto.setLeaderId(e.getLeaderId());
        dto.setLeaderInitials(e.getLeaderInitials());
        dto.setDeadline(e.getDeadline());
        dto.setSourceLang(e.getSourceLang());
        dto.setTargetLang(e.getTargetLang());
        dto.setPriority(e.getPriority());
        dto.setCover(e.getCover());
        dto.setDescription(e.getDescription());
        dto.setNotes(e.getNotes());
        dto.setIsRecruiting(e.getIsRecruiting());
        dto.setMaxMembers(e.getMaxMembers());
        return dto;
    }
}