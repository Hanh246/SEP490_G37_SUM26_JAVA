package com.sep.comiverse.controller;

import com.sep.comiverse.dto.CreateTaskRequest;
import com.sep.comiverse.dto.TeamMemberDto;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
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
    private final IProjectTeamRepository projectTeamRepository;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final IUserRepository userRepository;
    private final IPageTranslationRepository iPageTranslationRepository;
    private final NotificationService notificationService;

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

    // ── CHAPTER BACKLOG ──────────────────────────────
    @GetMapping("/{teamId}/chapter-backlog")
    public ResponseEntity<List<Map<String, Object>>> getChapterBacklog(@PathVariable UUID teamId) {
        ProjectTeamEntity team = projectTeamRepository.findById(teamId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Project team not found"));

        String comicName = team.getComicName();
        if (comicName == null || comicName.trim().isEmpty()) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        // Find the comic entity by name (case-insensitive)
        List<ComicEntity> comics = comicRepository.findAllByTitle(comicName);
        if (comics.isEmpty()) {
            comics = comicRepository.findAllByTitleIgnoreCase(comicName);
        }
        if (comics.isEmpty()) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        ComicEntity comic = comics.get(0);

        // Get all approved (published) chapters for this comic
        List<ChapterEntity> publishedChapters = chapterRepository.findAllByComic_IdAndDeletedFalseAndModerationStatus(
                comic.getId(),
                com.sep.comiverse.entity.enums.ChapterStatus.PUBLISHED
        );

        // Get all tasks for this project team
        List<TeamTaskEntity> teamTasks = taskRepository.findByProjectTeamId(teamId);

        // Find which chapter IDs already have a task associated with this team
        java.util.Set<UUID> taskChapterIds = teamTasks.stream()
                .map(t -> t.getChapter() != null ? t.getChapter().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // Filter out chapters that already have a task
        List<ChapterEntity> backlogChapters = publishedChapters.stream()
                .filter(c -> !taskChapterIds.contains(c.getId()))
                .toList();

        List<Map<String, Object>> result = backlogChapters.stream().map(c -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("chapterId", c.getId());
            map.put("chapterNumber", c.getChapterNumber());
            map.put("title", c.getTitle());
            map.put("comicName", comic.getTitle());
            map.put("pages", c.getImages() != null ? c.getImages().size() : 0);
            map.put("approvedAt", c.getCreatedAt());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
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
    public ResponseEntity<?> createTask(@PathVariable UUID teamId, @RequestBody CreateTaskRequest request) {

        ChapterEntity chapter = null;
        if (request.getChapterId() != null) {
            chapter = chapterRepository.findById(request.getChapterId()).orElse(null);
            if (chapter == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Chapter not found: " + request.getChapterId()));
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "chapterId is required"));
        }

        TeamTaskEntity task = TeamTaskEntity.builder()
                .projectTeamId(teamId)
                .title(request.getTitle())
                .status(request.getStatus())
                .assigneeIds(request.getAssigneeIds())
                .chapter(chapter)
                .dueDate(request.getDueDate())
                .build();

        TeamTaskEntity created = taskRepository.save(task);

        // === Copy ảnh từ chapter.images -> tạo bộ page_translation riêng cho task này ===
        List<String> images = chapter.getImages();
        if (images != null && !images.isEmpty()) {
            List<PageTranslationEntity> pages = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                pages.add(PageTranslationEntity.builder()
                        .taskId(created)
                        .imageUrl(images.get(i))
                        .pageNumber(i + 1)
                        .status(com.sep.comiverse.entity.enums.PageStatus.TODO)
                        .bubbles("[]")
                        .build());
            }
            iPageTranslationRepository.saveAll(pages);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @GetMapping("/{teamId}/members")
    public ResponseEntity<?> getTeamMembers(@PathVariable UUID teamId) {
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        List<UserEntity> members = team.getMembers() != null ? team.getMembers() : Collections.emptyList();
        if (members.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }


        List<TeamMemberDto> result = members.stream()
                .map(u -> TeamMemberDto.builder()
                        .id(u.getId())
                        .name(u.getFullName())
                        .avatar(computeInitials(u.getFullName()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

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
        var taskOpt = taskRepository.findByIdWithChapter(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Task not found"));
        }
        return ResponseEntity.ok(taskOpt.get());
    }

    // ── JOIN REQUESTS ────────────────────────────────
    @GetMapping("/{teamId}/requests")
    public ResponseEntity<List<TeamJoinRequestEntity>> getRequests(@PathVariable UUID teamId) {
        return ResponseEntity.ok(joinRequestRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/requests")
    public ResponseEntity<TeamJoinRequestEntity> createRequest(
            @PathVariable UUID teamId,
            @RequestBody TeamJoinRequestEntity request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        request.setProjectTeamId(teamId);
        if (principal != null) {
            request.setRequesterId(principal.getId());
        }
        TeamJoinRequestEntity saved = joinRequestRepository.save(request);
        projectTeamRepository.findById(teamId).ifPresent(team ->
                notificationService.notifyUser(
                        team.getLeaderId(),
                        "New team join request",
                        saved.getName() + " requested to join " + team.getTitle() + ".",
                        "INFO"
                )
        );
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/requests/{id}/decision")
    public ResponseEntity<TeamJoinRequestEntity> decideRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        TeamJoinRequestEntity request = joinRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        String decision = body == null ? "" : body.getOrDefault("decision", "").trim().toLowerCase();
        if (!"approved".equals(decision) && !"rejected".equals(decision)) {
            return ResponseEntity.badRequest().build();
        }

        ProjectTeamEntity team = projectTeamRepository.findById(request.getProjectTeamId()).orElse(null);
        if (team != null && "approved".equals(decision)) {
            team.setMembersCount((team.getMembersCount() == null ? 0 : team.getMembersCount()) + 1);
            projectTeamRepository.save(team);
        }

        String teamName = team == null ? "the translation team" : team.getTitle();
        notificationService.notifyUser(
                request.getRequesterId(),
                "Team request " + decision,
                "Your request to join " + teamName + " was " + decision + ".",
                "approved".equals(decision) ? "UPDATE" : "WARNING"
        );
        joinRequestRepository.deleteById(id);
        return ResponseEntity.ok(request);
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