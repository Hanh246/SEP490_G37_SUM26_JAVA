package com.sep.comiverse.controller;

import com.sep.comiverse.dto.TeamMemberDto;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team-workspace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamWorkspaceController {

    private final ITeamAnnouncementRepository announcementRepository;
    private final ITeamMessageRepository messageRepository;
    private final ITeamTaskRepository taskRepository;
    private final ITeamJoinRequestRepository joinRequestRepository;
    // ⚠️ 2 field MỚI cần thêm — đổi lại đúng tên interface thật trong project bạn nếu
    // khác (ví dụ IProjectTeamRepository/IUserRepository có thể tên khác). Nhờ có
    // @RequiredArgsConstructor ở trên class, chỉ cần khai báo field "private final" là
    // Spring tự inject qua constructor, không cần viết tay constructor.
    private final IProjectTeamRepository projectTeamRepository;
    private final IUserRepository userRepository;
    private final IChapterRepository chapterRepository;

    // ── ANNOUNCEMENTS ────────────────────────────────
    @GetMapping("/{teamId}/announcements")
    public ResponseEntity<List<TeamAnnouncementEntity>> getAnnouncements(@PathVariable UUID teamId) {
        return ResponseEntity.ok(announcementRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/announcements")
    public ResponseEntity<TeamAnnouncementEntity> createAnnouncement(@PathVariable UUID teamId, @RequestBody TeamAnnouncementEntity announcement) {
        announcement.setProjectTeamId(teamId);
        if (announcement.getLikes() == null) {
            announcement.setLikes(0);
        }
        return ResponseEntity.ok(announcementRepository.save(announcement));
    }

    @PutMapping("/announcements/{id}/like")
    public ResponseEntity<TeamAnnouncementEntity> likeAnnouncement(@PathVariable UUID id) {
        return announcementRepository.findById(id).map(ann -> {
            ann.setLikes(ann.getLikes() + 1);
            return ResponseEntity.ok(announcementRepository.save(ann));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── MESSAGES (CHAT) ──────────────────────────────
    @GetMapping("/{teamId}/messages")
    public ResponseEntity<List<TeamMessageEntity>> getMessages(@PathVariable UUID teamId) {
        return ResponseEntity.ok(messageRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/messages")
    public ResponseEntity<TeamMessageEntity> createMessage(@PathVariable UUID teamId, @RequestBody TeamMessageEntity message) {
        message.setProjectTeamId(teamId);
        return ResponseEntity.ok(messageRepository.save(message));
    }

    // ── TASKS ────────────────────────────────────────
    @GetMapping("/{teamId}/tasks")
    public ResponseEntity<List<TeamTaskEntity>> getTasks(@PathVariable UUID teamId) {
        return ResponseEntity.ok(taskRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/tasks")
    public ResponseEntity<TeamTaskEntity> createTask(@PathVariable UUID teamId, @RequestBody TeamTaskEntity task) {
        task.setProjectTeamId(teamId);
        return ResponseEntity.ok(taskRepository.save(task));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<?> getTeamMembers(@PathVariable UUID teamId) {
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        List<UUID> memberIds = team.getMemberIds() != null ? team.getMemberIds() : Collections.emptyList();
        if (memberIds.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<UserEntity> users = userRepository.findAllById(memberIds);

        List<TeamMemberDto> result = users.stream()
                .map(u -> TeamMemberDto.builder()
                        .id(u.getId())
                        .name(u.getFullName())
                        .avatar(computeInitials(u.getFullName()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Query TRỰC TIẾP qua repository (giống pattern taskRepository.findByProjectTeamId
    // đã dùng cho TeamTaskEntity) thay vì đọc qua team.getChaptersList() — collection
    // LAZY @OneToMany đôi khi trả rỗng do cache/filter ẩn khó đoán, query trực tiếp
    // đáng tin cậy hơn.
    // ⚠️ Cần thêm method này vào IChapterRepository nếu chưa có:
    //     List<ChapterEntity> findByProjectTeam_Id(UUID projectTeamId);
    @GetMapping("/{teamId}/chapters")
    public ResponseEntity<List<ChapterLiteDTO>> getTeamChapters(@PathVariable UUID teamId) {
        List<ChapterEntity> chapters = chapterRepository.findByProjectTeam_Id(teamId);

        List<ChapterLiteDTO> result = chapters.stream()
                .map(c -> ChapterLiteDTO.builder()
                        .id(c.getId())
                        .comicId(c.getComic() != null ? c.getComic().getId() : null)
                        .chapterNumber(c.getChapterNumber())
                        .title(c.getTitle())
                        .viewCount(c.getViewCount())
                        .isPremium(c.getIsPremium())
                        .createdAt(c.getCreatedAt())

                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private String computeInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Object> getTaskById(@PathVariable UUID id) {
        var taskOpt = taskRepository.findById(id);

        if (taskOpt.isPresent()) {
            // Nếu tìm thấy, trả về trực tiếp Entity (Spring sẽ tự chuyển sang JSON)
            return ResponseEntity.ok(taskOpt.get());
        } else {
            // Nếu không, trả về Map lỗi
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Task not found"
            ));
        }
    }

    // ── JOIN REQUESTS ────────────────────────────────
    @GetMapping("/{teamId}/requests")
    public ResponseEntity<List<TeamJoinRequestEntity>> getRequests(@PathVariable UUID teamId) {
        return ResponseEntity.ok(joinRequestRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/requests")
    public ResponseEntity<TeamJoinRequestEntity> createRequest(@PathVariable UUID teamId, @RequestBody TeamJoinRequestEntity request) {
        request.setProjectTeamId(teamId);
        return ResponseEntity.ok(joinRequestRepository.save(request));
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> deleteRequest(@PathVariable UUID id) {
        if (joinRequestRepository.existsById(id)) {
            joinRequestRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}