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

@RestController
@RequestMapping("/project-teams")
public class ProjectTeamController extends BaseController<ProjectTeamEntity, ProjectTeamDTO, UUID, PaginationSearchDTO> {

    private final IProjectTeamRepository iProjectTeamRepository;

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

        List<ProjectTeamEntity> teams = iProjectTeamRepository.findByMemberId(userId);
        List<ProjectTeamDTO> result = teams.stream().map(this::toDto).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ⚠️ "comicTitle" và "assignedToMe" trong ProjectTeamDTO không thấy field tương ứng
    // trực tiếp trên ProjectTeamEntity — để trống (null/false) tạm thời. Nếu 2 field này
    // cần tính toán từ đâu đó khác (VD assignedToMe dựa vào user hiện tại), báo mình bổ
    // sung logic đúng. "chaptersList" cũng để trống — trang danh sách project không cần
    // hiển thị chi tiết chapter ở đây (đã có API riêng /team-workspace/{teamId}/chapters).
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
        dto.setLeaderInitials(e.getLeaderInitials());
        dto.setDeadline(e.getDeadline());
        dto.setSourceLang(e.getSourceLang());
        dto.setTargetLang(e.getTargetLang());
        dto.setPriority(e.getPriority());
        dto.setCover(e.getCover());
        dto.setDescription(e.getDescription());
        dto.setNotes(e.getNotes());
        return dto;
    }
}