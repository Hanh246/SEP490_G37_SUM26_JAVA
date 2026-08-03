package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreateTaskRequest;
import com.sep.comiverse.dto.TeamMemberDto;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.NotificationService;
import com.sep.comiverse.service.UserPresenceService;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team-workspace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamWorkspaceController {

    private final ITeamAnnouncementRepository announcementRepository;
    private final ITeamPostCommentRepository postCommentRepository;
    private final ITeamMessageRepository messageRepository;
    private final ITeamTaskRepository taskRepository;
    private final ITeamJoinRequestRepository joinRequestRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final IUserRepository userRepository;
    private final IPageTranslationRepository iPageTranslationRepository;
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;
    private final SimpMessagingTemplate messagingTemplate;

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
    public ResponseEntity<TeamAnnouncementEntity> likeAnnouncement(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = principal.getId();
        return announcementRepository.findById(id).map(ann -> {
            String likedBy = ann.getLikedByUsers();
            if (likedBy == null) {
                likedBy = "";
            }
            String userIdStr = userId.toString();
            List<String> list = new ArrayList<>(Arrays.asList(likedBy.split(",")));
            list.removeIf(String::isEmpty);

            if (list.contains(userIdStr)) {
                list.remove(userIdStr);
                ann.setLikes(Math.max(0, (ann.getLikes() == null ? 0 : ann.getLikes()) - 1));
            } else {
                list.add(userIdStr);
                ann.setLikes((ann.getLikes() == null ? 0 : ann.getLikes()) + 1);
            }
            ann.setLikedByUsers(String.join(",", list));
            return ResponseEntity.ok(announcementRepository.save(ann));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/announcements/{id}/pin")
    public ResponseEntity<TeamAnnouncementEntity> pinAnnouncement(@PathVariable UUID id) {
        return announcementRepository.findById(id).map(ann -> {
            ann.setIsPinned(ann.getIsPinned() == null ? true : !ann.getIsPinned());
            return ResponseEntity.ok(announcementRepository.save(ann));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable UUID id) {
        if (announcementRepository.existsById(id)) {
            announcementRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/announcements/{announcementId}/comments")
    public ResponseEntity<List<TeamPostCommentEntity>> getComments(@PathVariable UUID announcementId) {
        return ResponseEntity.ok(postCommentRepository.findByAnnouncementIdOrderByTimeAsc(announcementId));
    }

    @PostMapping("/announcements/{announcementId}/comments")
    public ResponseEntity<TeamPostCommentEntity> createComment(@PathVariable UUID announcementId, @RequestBody TeamPostCommentEntity comment) {
        comment.setAnnouncementId(announcementId);
        if (comment.getLikes() == null) {
            comment.setLikes(0);
        }
        return ResponseEntity.ok(postCommentRepository.save(comment));
    }

    @PutMapping("/comments/{id}/like")
    public ResponseEntity<TeamPostCommentEntity> likeComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = principal.getId();
        return postCommentRepository.findById(id).map(comm -> {
            String likedBy = comm.getLikedByUsers();
            if (likedBy == null) {
                likedBy = "";
            }
            String userIdStr = userId.toString();
            List<String> list = new ArrayList<>(Arrays.asList(likedBy.split(",")));
            list.removeIf(String::isEmpty);

            if (list.contains(userIdStr)) {
                list.remove(userIdStr);
                comm.setLikes(Math.max(0, (comm.getLikes() == null ? 0 : comm.getLikes()) - 1));
            } else {
                list.add(userIdStr);
                comm.setLikes((comm.getLikes() == null ? 0 : comm.getLikes()) + 1);
            }
            comm.setLikedByUsers(String.join(",", list));
            return ResponseEntity.ok(postCommentRepository.save(comm));
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
        TeamMessageEntity saved = messageRepository.save(message);
        messagingTemplate.convertAndSend("/topic/team-workspace/" + teamId, saved);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{teamId}/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable UUID teamId, @PathVariable UUID messageId) {
        if (messageRepository.existsById(messageId)) {
            messageRepository.deleteById(messageId);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{teamId}/messages/warn")
    public ResponseEntity<TeamMessageEntity> warnMember(@PathVariable UUID teamId, @RequestBody Map<String, String> payload) {
        String memberName = payload.get("memberName");
        TeamMessageEntity warningMsg = TeamMessageEntity.builder()
                .projectTeamId(teamId)
                .sender("SYSTEM")
                .avatar("⚠️")
                .text("Member " + memberName + " has been warned by the Project Leader.")
                .time(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                .build();
        TeamMessageEntity saved = messageRepository.save(warningMsg);
        messagingTemplate.convertAndSend("/topic/team-workspace/" + teamId, saved);
        return ResponseEntity.ok(saved);
    }

    // ── TASKS ────────────────────────────────────────
    @GetMapping("/{teamId}/tasks")
    public ResponseEntity<List<TeamTaskEntity>> getTasks(@PathVariable UUID teamId) {
        return ResponseEntity.ok(taskRepository.findByProjectTeamId(teamId));
    }

    @PostMapping("/{teamId}/tasks")
    public ResponseEntity<?> createTask(
            @PathVariable UUID teamId,
            @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (!canManageTeamTasks(principal, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Only this team's Project Leader can create or assign tasks"));
        }

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

        UUID primaryAssigneeId = request.getAssigneeId();
        if (primaryAssigneeId == null && request.getAssigneeIds() != null && !request.getAssigneeIds().isEmpty()) {
            primaryAssigneeId = request.getAssigneeIds().get(0);
        }
        String assigneeError = validateTranslatorAssignee(
                teamId,
                primaryAssigneeId
        );
        if (assigneeError != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", assigneeError));
        }
        String initialStatus = request.getStatus();
        if (isCompletedStatus(initialStatus) && !canMarkTaskCompleted(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Only a Project Leader can mark a task as completed"));
        }
        TeamTaskEntity task = TeamTaskEntity.builder()
                .projectTeamId(teamId)
                .title(request.getTitle())
                .status(initialStatus)
                .assigneeId(primaryAssigneeId)
                .chapter(chapter)
                .dueDate(request.getDueDate())
                .completedAt(isCompletedStatus(initialStatus) ? Instant.now() : null)
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

        List<ProjectTeamMemberEntity> members = team.getMembers() != null
                ? new ArrayList<>(team.getMembers())
                : new ArrayList<>();

        if (team.getLeaderId() != null && members.stream().noneMatch(m -> team.getLeaderId().equals(m.getUser().getId()))) {
            userRepository.findById(team.getLeaderId()).ifPresent(u -> {
                ProjectTeamMemberEntity mockLeader = ProjectTeamMemberEntity.builder()
                        .team(team)
                        .user(u)
                        .build();
                mockLeader.setId(u.getId()); // fallback id
                mockLeader.setCreatedAt(team.getCreatedAt()); // leader joined when team was created
                members.add(mockLeader);
            });
        }

        if (members.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<TeamMemberDto> result = members.stream()
                .map(m -> {
                    UserEntity u = m.getUser();
                    return TeamMemberDto.builder()
                            .id(u.getId())
                            .name(u.getFullName())
                            .role(team.getLeaderId() != null && team.getLeaderId().equals(u.getId())
                                    ? "Group Leader"
                                    : "Member")
                            .avatar(computeInitials(u.getFullName()))
                            .online(userPresenceService.isOnline(u.getId()))
                            .lastSeenAt(u.getLastSeenAt())
                            .joinDate(m.getCreatedAt())
                            .build();
                })
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
        List<TeamJoinRequestEntity> requests = joinRequestRepository.findByProjectTeamId(teamId);
        for (TeamJoinRequestEntity request : requests) {
            if (request.getRequesterId() != null) {
                int activeProjects = (int) projectTeamRepository.countActiveTeamsByUserId(request.getRequesterId());
                int activeTasks = (int) taskRepository.countActiveTasksByAssigneeId(request.getRequesterId());
                request.setActiveProjectsCount(activeProjects);
                request.setActiveTasksCount(activeTasks);
            }
        }
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/requests/by-name")
    public ResponseEntity<List<TeamJoinRequestEntity>> getRequestsByName(@RequestParam String name) {
        return ResponseEntity.ok(joinRequestRepository.findByName(name));
    }

    @PostMapping("/{teamId}/requests")
    public ResponseEntity<?> createRequest(
            @PathVariable UUID teamId,
            @RequestBody TeamJoinRequestEntity request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (request.getName() != null && joinRequestRepository.existsByNameAndProjectTeamId(request.getName(), teamId)) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "You have already applied to this team!"));
        }
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
                        "INFO",
                        NotificationPreferenceKey.TEAM_JOIN_REQUESTS
                )
        );
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/requests/{id}/decision")
    public ResponseEntity<?> decideRequest(
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
            if (request.getRequesterId() != null) {
                userRepository.findById(request.getRequesterId()).ifPresent(user -> {
                    if (team.getMembers() == null) {
                        team.setMembers(new ArrayList<>());
                    }
                    boolean alreadyMember = team.getMembers().stream()
                            .anyMatch(member -> member.getUser().getId().equals(user.getId()));
                    if (!alreadyMember) {
                        ProjectTeamMemberEntity newMember = ProjectTeamMemberEntity.builder()
                                .team(team)
                                .user(user)
                                .build();
                        team.getMembers().add(newMember);
                    }
                });
            }
            team.setMembersCount(team.getMembers() == null ? 0 : team.getMembers().size());
            projectTeamRepository.save(team);
        }

        String teamName = team == null ? "the translation team" : team.getTitle();
        notificationService.notifyUser(
                request.getRequesterId(),
                "Team request " + decision,
                "Your request to join " + teamName + " was " + decision + ".",
                "approved".equals(decision) ? "UPDATE" : "WARNING",
                NotificationPreferenceKey.TEAM_UPDATES
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

    @DeleteMapping("/{teamId}/members/{memberId}")
    public ResponseEntity<?> removeMember(@PathVariable UUID teamId, @PathVariable UUID memberId) {
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (team.getMembers() != null) {
            boolean removed = team.getMembers().removeIf(m -> m.getUser().getId().equals(memberId));
            if (removed) {
                team.setMembersCount(team.getMembers().size());
                projectTeamRepository.save(team);
                return ResponseEntity.ok(Map.of("success", true));
            }
        }
        
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/tasks/{taskId}/submit-for-review")
    public ResponseEntity<?> submitForReview(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Task not found"));
        }
        if (!canSubmitTaskForReview(principal, task)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false,
                            "message", "Only the assigned Translator or this team's Project Leader can submit this task for review"));
        }
        if (isCompletedStatus(task.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "A completed task cannot be submitted for review again"));
        }

        task.setStatus("under_review");
        task.setCompletedAt(null);
        taskRepository.save(task);

        List<PageTranslationEntity> pages = iPageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);
        for (PageTranslationEntity page : pages) {
            page.setReviewBaselineBubbles(page.getBubbles());
        }
        iPageTranslationRepository.saveAll(pages);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<?> updateTask(
            @PathVariable UUID taskId,
            @RequestBody Map<String, Object> updates,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Task not found"));
        }
        if (!canManageTeamTasks(principal, task.getProjectTeamId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Only this team's Project Leader can edit tasks"));
        }

        if (updates.containsKey("title")) {
            task.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("status")) {
            String previousStatus = task.getStatus();
            String newStatus = updates.get("status") == null ? null : String.valueOf(updates.get("status"));
            if (isCompletedStatus(newStatus) && !canMarkTaskCompleted(principal)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Only a Project Leader can mark a task as completed"));
            }
            task.setStatus(newStatus);
            if (isCompletedStatus(newStatus) && (!isCompletedStatus(previousStatus) || task.getCompletedAt() == null)) {
                task.setCompletedAt(Instant.now());
            } else if (!isCompletedStatus(newStatus)) {
                task.setCompletedAt(null);
            }
        }
        if (updates.containsKey("dueDate")) {
            task.setDueDate((String) updates.get("dueDate"));
        }
        if (updates.containsKey("assigneeIds")) {
            Object rawAssigneeIds = updates.get("assigneeIds");
            UUID primaryAssigneeId = null;

            if (rawAssigneeIds instanceof List<?> rawIds
                    && !rawIds.isEmpty()
                    && rawIds.get(0) != null
                    && !String.valueOf(rawIds.get(0)).isBlank()) {
                try {
                    primaryAssigneeId = UUID.fromString(
                            String.valueOf(rawIds.get(0))
                    );
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "success", false,
                                    "message", "Invalid assignee ID format"
                            ));
                }
            }

            String assigneeError = validateTranslatorAssignee(
                    task.getProjectTeamId(),
                    primaryAssigneeId
            );

            if (assigneeError != null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", assigneeError
                        ));
            }

            task.setAssigneeId(primaryAssigneeId);
        }

        if (updates.containsKey("assigneeId")) {
            Object rawAssigneeId = updates.get("assigneeId");
            UUID primaryAssigneeId = null;

            if (rawAssigneeId != null
                    && !String.valueOf(rawAssigneeId).isBlank()) {
                try {
                    primaryAssigneeId = UUID.fromString(
                            String.valueOf(rawAssigneeId)
                    );
                } catch (IllegalArgumentException ex) {
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "success", false,
                                    "message", "Invalid assignee ID format"
                            ));
                }
            }

            String assigneeError = validateTranslatorAssignee(
                    task.getProjectTeamId(),
                    primaryAssigneeId
            );

            if (assigneeError != null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", assigneeError
                        ));
            }

            task.setAssigneeId(primaryAssigneeId);
        }
        if (isCompletedStatus(task.getStatus()) && task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
        }

        TeamTaskEntity saved = taskRepository.save(task);
        return ResponseEntity.ok(saved);
    }
    private boolean isCompletedStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "completed".equals(normalized) || "complete".equals(normalized) || "done".equals(normalized);
    }

    private boolean canMarkTaskCompleted(UserPrincipal principal) {
        return principal != null && "PROJECT_LEADER".equalsIgnoreCase(principal.getRole());
    }

    private boolean canSubmitTaskForReview(UserPrincipal principal, TeamTaskEntity task) {
        if (principal == null || principal.getId() == null || task == null) return false;
        if (canManageTeamTasks(principal, task.getProjectTeamId())) return true;
        if (!"TRANSLATOR".equalsIgnoreCase(principal.getRole())) return false;
        return principal.getId().equals(task.getAssigneeId());
    }

    private boolean canManageTeamTasks(UserPrincipal principal, UUID teamId) {
        if (!canMarkTaskCompleted(principal) || teamId == null) return false;
        return projectTeamRepository.findById(teamId)
                .map(team -> {
                    if (team.getLeaderId() != null) return principal.getId().equals(team.getLeaderId());
                    String leaderName = team.getLeaderName() == null ? "" : team.getLeaderName().trim();
                    return leaderName.equalsIgnoreCase(principal.getUsername())
                            || (principal.getFullName() != null && leaderName.equalsIgnoreCase(principal.getFullName()));
                })
                .orElse(false);
    }

    private String validateTranslatorAssignee(
            UUID teamId,
            UUID assigneeId
    ) {
        if (assigneeId == null) {
            return "A Translator assignee is required";
        }

        ProjectTeamEntity team = projectTeamRepository
                .findById(teamId)
                .orElse(null);

        if (team == null) {
            return "Project team not found";
        }

        List<ProjectTeamMemberEntity> teamMembers =
                team.getMembers() == null
                        ? List.of()
                        : team.getMembers();

        UserEntity member = teamMembers.stream()
                .filter(Objects::nonNull)
                .map(ProjectTeamMemberEntity::getUser)
                .filter(Objects::nonNull)
                .filter(user -> assigneeId.equals(user.getId()))
                .findFirst()
                .orElse(null);

        if (member == null) {
            return "The assignee must be an approved member of this project team";
        }

        String role = member.getRole() == null
                ? ""
                : member.getRole().getRoleName();

        if (!"TRANSLATOR".equalsIgnoreCase(role)) {
            return "Only users with the TRANSLATOR role can be assigned payout-eligible tasks";
        }

        return null;
    }

}