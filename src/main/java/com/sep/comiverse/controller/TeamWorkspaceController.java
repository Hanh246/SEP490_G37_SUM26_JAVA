package com.sep.comiverse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.CreateTaskRequest;
import com.sep.comiverse.dto.request.HandoverTaskRequest;
import com.sep.comiverse.dto.response.TaskHandoverResponse;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.dto.TeamMemberDto;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.NotificationService;
import com.sep.comiverse.service.UserPresenceService;
import com.sep.comiverse.service.TranslatorPaymentService;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;

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
    private final INotificationRepository notificationRepository;
    private final IPageTranslationRepository iPageTranslationRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;
    private final ITranslatorRepository translatorRepository;
    private final IReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    private final ITeamJoinBanRepository joinBanRepository;
    private final ITranslatorCooldownRepository cooldownRepository;
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TranslatorPaymentService translatorPaymentService;

    private static final int MAX_ACTIVE_TEAMS = 5;
    private static final int MAX_ACTIVE_TASKS = 5;
    private static final long CANCEL_COOLDOWN_HOURS = 12;
    private static final long LEAVE_COOLDOWN_HOURS = 24;

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

    @PutMapping("/announcements/{id}")
    public ResponseEntity<TeamAnnouncementEntity> updateAnnouncement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserPrincipal principal) {
        return announcementRepository.findById(id).map(ann -> {
            if (payload.containsKey("content")) {
                String newContent = (String) payload.get("content");
                if (newContent != null && !newContent.trim().isEmpty()) {
                    if (com.sep.comiverse.util.ProfanityFilterUtil.containsProfanity(newContent)) {
                        throw new com.sep.comiverse.exception.CustomException(400, "Your post contains inappropriate language.", org.springframework.http.HttpStatus.BAD_REQUEST);
                    }
                    ann.setContent(newContent.trim());
                    ann.setIsEdited(true);
                    ann.setUpdatedAt(java.time.LocalDateTime.now());
                }
            }
            if (payload.containsKey("imageUrl")) {
                ann.setImageUrl((String) payload.get("imageUrl"));
            }
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
        if (com.sep.comiverse.util.ProfanityFilterUtil.containsProfanity(comment.getContent())) {
            throw new com.sep.comiverse.exception.CustomException(400, "Your comment contains inappropriate language.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
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

    @PutMapping("/comments/{id}")
    public ResponseEntity<TeamPostCommentEntity> updateComment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        return postCommentRepository.findById(id).map(comment -> {
            String newContent = body.get("content");
            if (newContent == null || newContent.trim().isEmpty()) {
                throw new com.sep.comiverse.exception.CustomException(400, "Comment content cannot be empty.", org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            if (com.sep.comiverse.util.ProfanityFilterUtil.containsProfanity(newContent)) {
                throw new com.sep.comiverse.exception.CustomException(400, "Your comment contains inappropriate language.", org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            comment.setContent(newContent.trim());
            comment.setIsEdited(true);
            comment.setUpdatedAt(java.time.LocalDateTime.now());
            return ResponseEntity.ok(postCommentRepository.save(comment));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (postCommentRepository.existsById(id)) {
            postCommentRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
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

        Map<UUID, List<TeamTaskEntity>> tasksByChapter = teamTasks.stream()
                .filter(t -> t.getChapter() != null && !isSupersededStatus(t.getStatus()))
                .collect(Collectors.groupingBy(t -> t.getChapter().getId()));

        Map<UUID, TeamTaskEntity> latestPreviousTaskByChapter = new HashMap<>();
        for (TeamTaskEntity teamTask : teamTasks) {
            if (teamTask.getChapter() == null || isSupersededStatus(teamTask.getStatus())) {
                continue;
            }
            UUID chapterId = teamTask.getChapter().getId();
            TeamTaskEntity current = latestPreviousTaskByChapter.get(chapterId);
            if (current == null || compareTaskRecency(teamTask, current) > 0) {
                latestPreviousTaskByChapter.put(chapterId, teamTask);
            }
        }

        List<Map<String, Object>> result = publishedChapters.stream()
                .filter(c -> {
                    List<TeamTaskEntity> existing = tasksByChapter.getOrDefault(c.getId(), List.of());
                    if (canCreateRevisionTask(c.getId(), team, existing)) {
                        return true;
                    }
                    return existing.isEmpty() && !isTeamTranslationPublished(c.getId(), team);
                })
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    List<TeamTaskEntity> existing = tasksByChapter.getOrDefault(c.getId(), List.of());
                    boolean revision = canCreateRevisionTask(c.getId(), team, existing);
                    TeamTaskEntity previousTask = latestPreviousTaskByChapter.get(c.getId());
                    map.put("chapterId", c.getId());
                    map.put("chapterNumber", c.getChapterNumber());
                    map.put("title", c.getTitle());
                    map.put("comicName", comic.getTitle());
                    map.put("pages", c.getImages() != null ? c.getImages().size() : 0);
                    map.put("approvedAt", c.getCreatedAt());
                    map.put("revision", revision);
                    map.put("canCreateTask", true);
                    map.put("previousTaskId", previousTask != null ? previousTask.getId() : null);
                    map.put("resolutionNote", revision ? findLatestTranslationReportNote(c.getId(), team) : null);
                    return map;
                })
                .toList();

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
    public ResponseEntity<TeamMessageEntity> warnMember(
            @PathVariable UUID teamId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal principal) {
        String memberName = payload.get("memberName");
        String memberIdStr = payload.get("memberId");
        String reason = payload.getOrDefault("reason", "Violation of group chat guidelines or translation quality standards");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Violation of group chat guidelines or translation quality standards";
        }

        // 1. Get Project Team info for context
        ProjectTeamEntity projectTeam = projectTeamRepository.findById(teamId).orElse(null);
        String projectTitle = projectTeam != null ? (projectTeam.getTitle() != null ? projectTeam.getTitle() : projectTeam.getComicName()) : "Project Team";
        String leaderName = (principal != null && principal.getUsername() != null) ? principal.getUsername() : (projectTeam != null ? projectTeam.getLeaderName() : "Project Leader");

        // 2. Find target UserEntity
        UserEntity targetUser = null;
        if (memberIdStr != null && !memberIdStr.trim().isEmpty()) {
            try {
                UUID memberId = UUID.fromString(memberIdStr);
                targetUser = userRepository.findById(memberId).orElse(null);
            } catch (Exception ignored) {}
        }
        if (targetUser == null && memberName != null && !memberName.trim().isEmpty()) {
            targetUser = userRepository.findByUsername(memberName).orElse(null);
            if (targetUser == null) {
                // Try finding by full name or email if possible
                List<UserEntity> allUsers = userRepository.findAll();
                targetUser = allUsers.stream()
                        .filter(u -> memberName.equalsIgnoreCase(u.getFullName()) || memberName.equalsIgnoreCase(u.getUsername()) || memberName.equalsIgnoreCase(u.getEmail()))
                        .findFirst()
                        .orElse(null);
            }
        }

        // 3. Persist Notification in DB for target user
        if (targetUser != null) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(targetUser)
                    .title("⚠️ Cảnh báo từ Nhóm dịch [" + projectTitle + "]")
                    .message("Trưởng nhóm (" + leaderName + ") đã gửi cảnh báo cho bạn: \"" + reason + "\". Vui lòng kiểm tra và tuân thủ nội quy nhóm.")
                    .type("WARNING")
                    .actionUrl("/translator/projects")
                    .isRead(false)
                    .build();
            NotificationEntity savedNotif = notificationRepository.save(notification);

            // Broadcast real-time notification to user's personal channel
            NotificationResponse notifResponse = NotificationResponse.builder()
                    .id(savedNotif.getId())
                    .title(savedNotif.getTitle())
                    .message(savedNotif.getMessage())
                    .type(savedNotif.getType())
                    .actionUrl(savedNotif.getActionUrl())
                    .isRead(savedNotif.getIsRead())
                    .createdAt(savedNotif.getCreatedAt())
                    .build();
            messagingTemplate.convertAndSend("/topic/notifications/" + targetUser.getId(), notifResponse);
        }

        // 4. Save and broadcast system message to group chat
        TeamMessageEntity warningMsg = TeamMessageEntity.builder()
                .projectTeamId(teamId)
                .sender("SYSTEM")
                .avatar("⚠️")
                .text("⚠️ [CẢNH BÁO / WARNING] @" + (memberName != null ? memberName : "Thành viên") + " đã bị nhắc nhở bởi Trưởng nhóm. Lý do: " + reason)
                .time(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                .build();
        TeamMessageEntity saved = messageRepository.save(warningMsg);
        messagingTemplate.convertAndSend("/topic/team-workspace/" + teamId, saved);

        return ResponseEntity.ok(saved);
    }

    // ── TASKS ────────────────────────────────────────
    @GetMapping("/{teamId}/tasks")
    public ResponseEntity<List<Map<String, Object>>> getTasks(@PathVariable UUID teamId) {
        return ResponseEntity.ok(taskRepository.findByProjectTeamId(teamId).stream()
                .map(this::toTaskResponse)
                .toList());
    }

    @GetMapping("/tasks/by-chapter/{chapterId}")
    public ResponseEntity<List<Map<String, Object>>> getTasksByChapter(@PathVariable UUID chapterId) {
        return ResponseEntity.ok(taskRepository.findByChapter_Id(chapterId).stream()
                .map(this::toTaskResponse)
                .toList());
    }

    private Map<String, Object> toTaskResponse(TeamTaskEntity task) {
        List<PageTranslationEntity> taskPages = iPageTranslationRepository
                .findByTaskId_IdOrderByPageNumberAsc(task.getId());
        long completedPages = taskPages.stream()
                .filter(page -> page.getStatus() == com.sep.comiverse.entity.enums.PageStatus.DONE)
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", task.getId());
        response.put("projectTeamId", task.getProjectTeamId());
        response.put("title", task.getTitle());
        response.put("status", task.getStatus());
        response.put("taskType", task.getTaskType() == null ? "REGULAR" : task.getTaskType());
        ProjectTeamEntity taskTeam = task.getProjectTeam() != null
                ? task.getProjectTeam()
                : projectTeamRepository.findById(task.getProjectTeamId()).orElse(null);
        response.put("needsRevision", task.getChapter() != null && canCreateRevisionTask(task.getChapter().getId(), taskTeam));
        response.put("assigneeId", task.getAssigneeId());
        response.put("dueDate", task.getDueDate());
        response.put("chapterRewardUsd", task.getChapterRewardUsd());
        response.put("rejectionReason", task.getRejectionReason());
        response.put("completedAt", task.getCompletedAt());
        response.put("settledAt", task.getSettledAt());
        response.put("totalPages", taskPages.size());
        response.put("completedPages", completedPages);

        ChapterEntity chapter = task.getChapter();
        response.put("chapterId", chapter == null ? null : chapter.getId());
        response.put("chapterNumber", chapter == null ? null : chapter.getChapterNumber());
        response.put("chapterTitle", chapter == null ? null : chapter.getTitle());
        if (chapter != null) {
            Map<String, Object> chapterResponse = new LinkedHashMap<>();
            chapterResponse.put("id", chapter.getId());
            chapterResponse.put("chapterNumber", chapter.getChapterNumber());
            chapterResponse.put("title", chapter.getTitle());
            response.put("chapter", chapterResponse);
        } else {
            response.put("chapter", null);
        }
        return response;
    }

    @Transactional
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
        if (primaryAssigneeId != null) {
            String assigneeError = validateTranslatorAssignee(
                    teamId,
                    primaryAssigneeId
            );
            if (assigneeError != null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", assigneeError));
            }
        }
        if (isCompletedStatus(request.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false,
                            "message", "A task can only become completed after all pages are DONE and the Project Leader approves the review"));
        }
        String initialStatus = "backlog";
        List<TeamTaskEntity> existingTeamChapterTasks = taskRepository.findByChapter_Id(chapter.getId()).stream()
                .filter(t -> teamId.equals(t.getProjectTeamId()))
                .toList();
        List<TeamTaskEntity> currentTeamChapterTasks = existingTeamChapterTasks.stream()
                .filter(t -> !isSupersededStatus(t.getStatus()))
                .toList();
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        boolean canRevise = canCreateRevisionTask(chapter.getId(), team, currentTeamChapterTasks);
        if (!currentTeamChapterTasks.isEmpty() && !canRevise) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "This chapter already has a task in this project"));
        }

        if (currentTeamChapterTasks.isEmpty() && !canRevise && isTeamTranslationPublished(chapter.getId(), team)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false,
                            "message", "This chapter already has a published translation. A revision task can be created after a translation report is accepted"));
        }

        List<String> images = chapter.getImages();
        if (images == null || images.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Chapter has no pages"));
        }
        java.math.BigDecimal chapterRewardUsd = request.getChapterRewardUsd();
        if (chapterRewardUsd == null || chapterRewardUsd.signum() <= 0) {
            chapterRewardUsd = translatorPaymentService.deriveChapterRewardUsd(images.size());
        } else {
            chapterRewardUsd = chapterRewardUsd.setScale(2, java.math.RoundingMode.HALF_UP);
        }

        Map<Integer, String> pageBubblesMap = collectPreviousTranslationBubbles(chapter.getId(), teamId, team);
        String taskTypeVal = request.getTaskType() != null && !request.getTaskType().isBlank()
                ? request.getTaskType()
                : (canRevise ? "REVISION" : "REGULAR");
        if (canRevise && (taskTypeVal.equalsIgnoreCase("REGULAR") || taskTypeVal.isBlank())) {
            taskTypeVal = "REVISION";
        }

        String revisionReason = canRevise ? findLatestTranslationReportNote(chapter.getId(), team) : null;

        if (canRevise && !currentTeamChapterTasks.isEmpty()) {
            for (TeamTaskEntity existing : currentTeamChapterTasks) {
                existing.setStatus("superseded");
                if (existing.getRejectionReason() == null || existing.getRejectionReason().isBlank()) {
                    existing.setRejectionReason(revisionReason);
                }
            }
            taskRepository.saveAll(currentTeamChapterTasks);
        }

        TeamTaskEntity task = TeamTaskEntity.builder()
                .projectTeamId(teamId)
                .title(request.getTitle())
                .status(initialStatus)
                .assigneeId(primaryAssigneeId)
                .chapter(chapter)
                .taskType(taskTypeVal)
                .dueDate(request.getDueDate())
                .chapterRewardUsd(chapterRewardUsd)
                .rejectionReason(revisionReason)
                .completedAt(null)
                .build();

        TeamTaskEntity created = taskRepository.save(task);

        List<PageTranslationEntity> pages = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            int pageNum = i + 1;
            String existingBubbles = pageBubblesMap.getOrDefault(pageNum, "[]");
            pages.add(PageTranslationEntity.builder()
                    .taskId(created)
                    .imageUrl(images.get(i))
                    .pageNumber(pageNum)
                    .assignedTranslatorId(primaryAssigneeId)
                    .responsibilityFactor(java.math.BigDecimal.ONE.setScale(2))
                    .status(com.sep.comiverse.entity.enums.PageStatus.TODO)
                    .bubbles(existingBubbles)
                    .build());
        }
        iPageTranslationRepository.saveAll(pages);

        return ResponseEntity.status(HttpStatus.CREATED).body(toTaskResponse(created));
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
        // Only return PENDING requests to the leader's review queue
        List<TeamJoinRequestEntity> pendingRequests = requests.stream()
                .filter(r -> r.getStatus() == null || "PENDING".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
        for (TeamJoinRequestEntity request : pendingRequests) {
            if (request.getRequesterId() != null) {
                int activeProjects = (int) projectTeamRepository.countActiveTeamsByUserId(request.getRequesterId());
                int activeTasks = (int) taskRepository.countActiveTasksByAssigneeId(request.getRequesterId());
                request.setActiveProjectsCount(activeProjects);
                request.setActiveTasksCount(activeTasks);
            }
        }
        return ResponseEntity.ok(pendingRequests);
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
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }
        UUID userId = principal.getId();

        // 1. Check if already applied (PENDING) to this specific team
        if (joinRequestRepository.existsByRequesterIdAndProjectTeamIdAndStatus(userId, teamId, "PENDING")) {
            return ResponseEntity.badRequest().body(Map.of("message", "You have already applied to this team!"));
        }

        // 2. Check if banned from this team
        if (joinBanRepository.existsByProjectTeamIdAndUserId(teamId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You are banned from applying to this team."));
        }

        // 3. Check cooldown (cancel / leave)
        List<TranslatorCooldownEntity> activeCooldowns = cooldownRepository.findActiveCooldowns(userId, Instant.now());
        if (!activeCooldowns.isEmpty()) {
            TranslatorCooldownEntity cd = activeCooldowns.get(0);
            long remainingMinutes = java.time.Duration.between(Instant.now(), cd.getCooldownUntil()).toMinutes();
            String timeLabel = remainingMinutes >= 60
                    ? (remainingMinutes / 60) + "h " + (remainingMinutes % 60) + "m"
                    : remainingMinutes + " minutes";
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "message", "You are on cooldown. Please wait " + timeLabel + " before applying.",
                    "cooldownUntil", cd.getCooldownUntil().toString(),
                    "cooldownType", cd.getCooldownType()
            ));
        }

        // 4. Check max 5 slots (joined teams + pending applications)
        long joinedTeams = projectTeamRepository.countActiveTeamsByUserId(userId);
        long pendingApps = joinRequestRepository.countByRequesterIdAndStatus(userId, "PENDING");
        long usedSlots = joinedTeams + pendingApps;
        if (usedSlots >= MAX_ACTIVE_TEAMS) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "You have reached the maximum of " + MAX_ACTIVE_TEAMS + " active teams/applications. Cancel a pending application or leave a team first.",
                    "joinedTeams", joinedTeams,
                    "pendingApplications", pendingApps,
                    "maxSlots", MAX_ACTIVE_TEAMS
            ));
        }

        // 5. Check max active tasks (max 5 active tasks allowed per translator)
        long activeTasks = taskRepository.countActiveTasksByAssigneeId(userId);
        if (activeTasks >= MAX_ACTIVE_TASKS) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Bạn đang xử lý tối đa " + MAX_ACTIVE_TASKS + " công việc dịch thuật cùng lúc. Vui lòng hoàn thành công việc trước khi xin gia nhập nhóm mới.",
                    "activeTasks", activeTasks,
                    "maxTasks", MAX_ACTIVE_TASKS
            ));
        }

        // All checks passed — create the request
        request.setProjectTeamId(teamId);
        request.setRequesterId(userId);
        request.setStatus("PENDING");

        // Fetch Translator Profile to inject CV and Bio
        translatorRepository.findByUser_Id(userId).ifPresent(translator -> {
            request.setCvUrl(translator.getCvUrl());
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                request.setText(translator.getBio());
            } else {
                request.setText(translator.getBio() + "\n\n---\nMessage: " + request.getText());
            }
        });

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
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This request is no longer pending."));
        }

        String decision = body == null ? "" : body.getOrDefault("decision", "").trim().toLowerCase();
        if (!"approved".equals(decision) && !"rejected".equals(decision)) {
            return ResponseEntity.badRequest().build();
        }

        ProjectTeamEntity team = projectTeamRepository.findById(request.getProjectTeamId()).orElse(null);

        if ("approved".equals(decision)) {
            // Before approving, check if the team has already reached its maximum capacity
            if (team != null) {
                int currentMembers = team.getMembersCount() != null ? team.getMembersCount() : (team.getMembers() != null ? team.getMembers().size() : 0);
                int maxMembers = team.getMaxMembers() != null ? team.getMaxMembers() : 5;
                if (currentMembers >= maxMembers) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "This project team has already reached its maximum capacity of " + maxMembers + " members. Cannot approve."
                    ));
                }
            }

            // Before approving, check if the translator already hit 5 active teams
            if (request.getRequesterId() != null) {
                long joinedTeams = projectTeamRepository.countActiveTeamsByUserId(request.getRequesterId());
                if (joinedTeams >= MAX_ACTIVE_TEAMS) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "This translator has already reached the maximum of " + MAX_ACTIVE_TEAMS + " active teams. Cannot approve."
                    ));
                }
            }

            if (team != null && request.getRequesterId() != null) {
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
                team.setMembersCount(team.getMembers() == null ? 0 : team.getMembers().size());
                projectTeamRepository.save(team);
            }

            // Mark this request as APPROVED
            request.setStatus("APPROVED");
            request.setDecidedAt(Instant.now());
            joinRequestRepository.save(request);

            // Auto-withdraw excess pending applications if translator hit 5 slots
            if (request.getRequesterId() != null) {
                long newJoinedCount = projectTeamRepository.countActiveTeamsByUserId(request.getRequesterId());
                long remainingPending = joinRequestRepository.countByRequesterIdAndStatus(request.getRequesterId(), "PENDING");
                if (newJoinedCount + remainingPending > MAX_ACTIVE_TEAMS) {
                    // Need to auto-withdraw some pending apps
                    long excessCount = (newJoinedCount + remainingPending) - MAX_ACTIVE_TEAMS;
                    List<TeamJoinRequestEntity> pendingApps = joinRequestRepository.findByRequesterIdAndStatus(request.getRequesterId(), "PENDING");
                    int withdrawn = 0;
                    for (TeamJoinRequestEntity pending : pendingApps) {
                        if (withdrawn >= excessCount) break;
                        pending.setStatus("AUTO_WITHDRAWN");
                        pending.setDecidedAt(Instant.now());
                        joinRequestRepository.save(pending);
                        withdrawn++;

                        // Notify the translator
                        notificationService.notifyUser(
                                pending.getRequesterId(),
                                "Application auto-withdrawn",
                                "Your application to " + getTeamName(pending.getProjectTeamId()) + " was auto-withdrawn because you've reached the maximum of " + MAX_ACTIVE_TEAMS + " active teams.",
                                "WARNING",
                                NotificationPreferenceKey.TEAM_UPDATES
                        );
                    }
                }
            }

            // Auto-reject remaining pending applications for this team if it reached max capacity
            if (team != null) {
                int newMembersCount = team.getMembersCount() != null ? team.getMembersCount() : 0;
                int maxMembers = team.getMaxMembers() != null ? team.getMaxMembers() : 5;
                if (newMembersCount >= maxMembers) {
                    List<TeamJoinRequestEntity> remainingApps = joinRequestRepository.findByProjectTeamId(team.getId()).stream()
                            .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                            .toList();
                    for (TeamJoinRequestEntity pendingApp : remainingApps) {
                        pendingApp.setStatus("REJECTED");
                        pendingApp.setDecidedAt(Instant.now());
                        joinRequestRepository.save(pendingApp);

                        notificationService.notifyUser(
                                pendingApp.getRequesterId(),
                                "Application auto-rejected",
                                "Your application to " + team.getTitle() + " was automatically rejected because the team has reached its maximum capacity.",
                                "WARNING",
                                NotificationPreferenceKey.TEAM_UPDATES
                        );
                    }
                }
            }
        } else {
            // REJECTED
            request.setStatus("REJECTED");
            request.setDecidedAt(Instant.now());
            joinRequestRepository.save(request);
        }

        String teamName = team == null ? "the translation team" : team.getTitle();
        notificationService.notifyUser(
                request.getRequesterId(),
                "Team request " + decision,
                "Your request to join " + teamName + " was " + decision + ".",
                "approved".equals(decision) ? "UPDATE" : "WARNING",
                NotificationPreferenceKey.TEAM_UPDATES
        );
        return ResponseEntity.ok(request);
    }

    // ── CANCEL APPLICATION ──
    @PutMapping("/requests/{id}/cancel")
    public ResponseEntity<?> cancelRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }
        TeamJoinRequestEntity request = joinRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only pending applications can be cancelled."));
        }
        // Only the requester can cancel their own request
        if (!principal.getId().equals(request.getRequesterId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You can only cancel your own applications."));
        }

        request.setStatus("CANCELLED");
        request.setCancelledAt(Instant.now());
        joinRequestRepository.save(request);

        // Create 12-hour cooldown
        cooldownRepository.save(TranslatorCooldownEntity.builder()
                .userId(principal.getId())
                .cooldownType("CANCEL")
                .cooldownUntil(Instant.now().plusSeconds(CANCEL_COOLDOWN_HOURS * 3600))
                .relatedTeamId(request.getProjectTeamId())
                .build());

        return ResponseEntity.ok(Map.of("success", true, "message", "Application cancelled. You are on a " + CANCEL_COOLDOWN_HOURS + "-hour cooldown before you can apply again."));
    }

    // ── BAN / UNBAN ──
    @PostMapping("/{teamId}/ban/{userId}")
    public ResponseEntity<?> banUser(
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        // Only the team leader can ban
        if (!principal.getId().equals(team.getLeaderId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only the team leader can ban members."));
        }
        if (joinBanRepository.existsByProjectTeamIdAndUserId(teamId, userId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "This user is already banned from this team."));
        }

        String reason = body != null ? body.getOrDefault("reason", "No reason provided") : "No reason provided";

        // Auto-reject any pending application from this user
        List<TeamJoinRequestEntity> pendingFromUser = joinRequestRepository.findByRequesterIdAndStatus(userId, "PENDING");
        for (TeamJoinRequestEntity req : pendingFromUser) {
            if (req.getProjectTeamId().equals(teamId)) {
                req.setStatus("REJECTED");
                req.setDecidedAt(Instant.now());
                joinRequestRepository.save(req);
            }
        }

        joinBanRepository.save(TeamJoinBanEntity.builder()
                .projectTeamId(teamId)
                .userId(userId)
                .bannedBy(principal.getId())
                .reason(reason)
                .build());

        notificationService.notifyUser(
                userId,
                "Banned from team",
                "You have been banned from " + team.getTitle() + ". Reason: " + reason,
                "WARNING",
                NotificationPreferenceKey.TEAM_UPDATES
        );

        return ResponseEntity.ok(Map.of("success", true, "message", "User banned from this team."));
    }

    @DeleteMapping("/{teamId}/ban/{userId}")
    public ResponseEntity<?> unbanUser(
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProjectTeamEntity team = projectTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        if (!principal.getId().equals(team.getLeaderId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only the team leader can unban members."));
        }

        joinBanRepository.findByProjectTeamIdAndUserId(teamId, userId).ifPresent(joinBanRepository::delete);
        return ResponseEntity.ok(Map.of("success", true, "message", "User unbanned."));
    }

    @GetMapping("/{teamId}/bans")
    public ResponseEntity<?> getBannedUsers(@PathVariable UUID teamId) {
        return ResponseEntity.ok(joinBanRepository.findByProjectTeamId(teamId));
    }

    // ── MY APPLICATION STATUS ──
    @GetMapping("/my-application-status")
    public ResponseEntity<?> getMyApplicationStatus(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = principal.getId();
        long joinedTeams = projectTeamRepository.countActiveTeamsByUserId(userId);
        long pendingApps = joinRequestRepository.countByRequesterIdAndStatus(userId, "PENDING");
        long usedSlots = joinedTeams + pendingApps;
        long availableSlots = Math.max(0, MAX_ACTIVE_TEAMS - usedSlots);

        // Check active cooldown
        List<TranslatorCooldownEntity> activeCooldowns = cooldownRepository.findActiveCooldowns(userId, Instant.now());
        String cooldownUntil = null;
        String cooldownType = null;
        if (!activeCooldowns.isEmpty()) {
            TranslatorCooldownEntity cd = activeCooldowns.get(0);
            cooldownUntil = cd.getCooldownUntil().toString();
            cooldownType = cd.getCooldownType();
        }

        // Get list of pending application team IDs
        List<TeamJoinRequestEntity> pendingList = joinRequestRepository.findByRequesterIdAndStatus(userId, "PENDING");
        List<Map<String, Object>> pendingDetails = pendingList.stream().map(req -> {
            Map<String, Object> m = new HashMap<>();
            m.put("requestId", req.getId());
            m.put("projectTeamId", req.getProjectTeamId());
            m.put("appliedAt", req.getTime());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "joinedTeams", joinedTeams,
                "pendingApplications", pendingApps,
                "usedSlots", usedSlots,
                "availableSlots", availableSlots,
                "maxSlots", MAX_ACTIVE_TEAMS,
                "cooldownUntil", cooldownUntil != null ? cooldownUntil : "",
                "cooldownType", cooldownType != null ? cooldownType : "",
                "pendingDetails", pendingDetails
        ));
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

        if (team.getLeaderId() != null && team.getLeaderId().equals(memberId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cannot remove the Group Leader from the team."));
        }

        if (team.getMembers() != null) {
            boolean removed = team.getMembers().removeIf(m -> m.getUser().getId().equals(memberId));
            if (removed) {
                team.setMembersCount(team.getMembers().size());
                projectTeamRepository.save(team);

                // Create 24-hour cooldown for the leaving member
                cooldownRepository.save(TranslatorCooldownEntity.builder()
                        .userId(memberId)
                        .cooldownType("LEAVE")
                        .cooldownUntil(Instant.now().plusSeconds(LEAVE_COOLDOWN_HOURS * 3600))
                        .relatedTeamId(teamId)
                        .build());

                return ResponseEntity.ok(Map.of("success", true, "message", "Member removed. A " + LEAVE_COOLDOWN_HOURS + "-hour cooldown has been applied."));
            }
        }

        return ResponseEntity.notFound().build();
    }

    /** Helper to get team name by ID */
    private String getTeamName(UUID teamId) {
        return projectTeamRepository.findById(teamId)
                .map(ProjectTeamEntity::getTitle)
                .orElse("a translation team");
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

        try {
            translatorPaymentService.validateReadyForReview(taskId);
        } catch (com.sep.comiverse.exception.CustomException ex) {
            return ResponseEntity.status(ex.getHttpStatus()).body(Map.of("success", false, "message", ex.getMessage()));
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
            if (isCompletedStatus(newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false,
                                "message", "Use the review approval flow to complete a task after every page is DONE"));
            }
            if (isBacklogStatus(previousStatus) && isBacklogStatus(newStatus)) {
                newStatus = "in_progress";
            }
            task.setStatus(newStatus);
            if (isCompletedStatus(newStatus) && (!isCompletedStatus(previousStatus) || task.getCompletedAt() == null)) {
                task.setCompletedAt(Instant.now());
            } else if (!isCompletedStatus(newStatus)) {
                task.setCompletedAt(null);
            }
        } else if (isBacklogStatus(task.getStatus())) {
            task.setStatus("in_progress");
        }
        if (updates.containsKey("dueDate")) {
            task.setDueDate((String) updates.get("dueDate"));
        }
        if (updates.containsKey("chapterRewardUsd")) {
            try {
                java.math.BigDecimal reward = new java.math.BigDecimal(String.valueOf(updates.get("chapterRewardUsd")))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                if (reward.signum() <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Chapter reward must be greater than zero"));
                }
                if (task.getSettledAt() != null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("success", false, "message", "A settled chapter reward cannot be changed"));
                }
                task.setChapterRewardUsd(reward);
            } catch (RuntimeException ex) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid chapter reward"));
            }
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

            UUID previousAssigneeId = task.getAssigneeId();
            if (previousAssigneeId != null && !previousAssigneeId.equals(primaryAssigneeId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false,
                                "message", "Use the handover endpoint when changing an assignee so completed pages and coefficient K are preserved"));
            }
            task.setAssigneeId(primaryAssigneeId);
            if (previousAssigneeId == null && primaryAssigneeId != null) {
                List<PageTranslationEntity> pages = iPageTranslationRepository
                        .findByTaskId_IdOrderByPageNumberAsc(task.getId());
                translatorPaymentService.initializePageAssignments(task, pages);
            }
        }
        if (isCompletedStatus(task.getStatus()) && task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
        }

        TeamTaskEntity saved = taskRepository.save(task);
        return ResponseEntity.ok(saved);
    }
    @PutMapping("/tasks/{taskId}/handover")
    public ResponseEntity<?> handoverTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody HandoverTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Task not found"));
        }
        if (!canManageTeamTasks(principal, task.getProjectTeamId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Only this team's Project Leader can hand over a task"));
        }
        String assigneeError = validateTranslatorAssignee(task.getProjectTeamId(), request.getNewAssigneeId());
        if (assigneeError != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", assigneeError));
        }
        TaskHandoverResponse response = translatorPaymentService.handover(task, request, principal.getId());
        return ResponseEntity.ok(response);
    }

    private boolean isCompletedStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "completed".equals(normalized) || "complete".equals(normalized)
                || "done".equals(normalized) || "published".equals(normalized);
    }

    private boolean isBacklogStatus(String status) {
        if (status == null || status.isBlank()) return true;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "backlog".equals(normalized) || "todo".equals(normalized);
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

        long activeTasks = taskRepository.countActiveTasksByAssigneeId(assigneeId);
        if (activeTasks >= MAX_ACTIVE_TASKS) {
            return "Dịch giả này đang xử lý tối đa " + MAX_ACTIVE_TASKS + " công việc cùng lúc, không thể giao thêm task mới.";
        }

        return null;
    }
    @GetMapping("/{teamId}/chapters")
    public ResponseEntity<List<Map<String, Object>>> getTeamChapters(
            @PathVariable UUID teamId
    ) {
        ProjectTeamEntity team = projectTeamRepository.findById(teamId)
                .orElseThrow(() ->
                        new jakarta.persistence.EntityNotFoundException(
                                "Project team not found"
                        )
                );

        String comicName = team.getComicName();
        if (comicName == null || comicName.isBlank()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<ComicEntity> comics = comicRepository.findAllByTitle(comicName);

        if (comics.isEmpty()) {
            comics = comicRepository.findAllByTitleIgnoreCase(comicName);
        }

        if (comics.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        ComicEntity comic = comics.get(0);

        List<ChapterEntity> chapters =
                chapterRepository
                        .findAllByComic_IdAndDeletedFalseAndModerationStatus(
                                comic.getId(),
                                ChapterStatus.PUBLISHED
                        );

        List<TeamTaskEntity> teamTasks = taskRepository.findByProjectTeamId(teamId);
        Map<UUID, List<TeamTaskEntity>> currentTasksByChapter = teamTasks.stream()
                .filter(t -> t.getChapter() != null && !isSupersededStatus(t.getStatus()))
                .collect(Collectors.groupingBy(t -> t.getChapter().getId()));

        List<Map<String, Object>> result = chapters.stream()
                .map(chapter -> {
                    Map<String, Object> item = new HashMap<>();
                    List<TeamTaskEntity> existing = currentTasksByChapter.getOrDefault(chapter.getId(), List.of());
                    boolean revision = canCreateRevisionTask(chapter.getId(), team, existing);

                    item.put("id", chapter.getId());
                    item.put("chapterId", chapter.getId());
                    item.put("chapterNumber", chapter.getChapterNumber());
                    item.put("title", chapter.getTitle());
                    item.put("comicId", comic.getId());
                    item.put("comicName", comic.getTitle());
                    item.put(
                            "pages",
                            chapter.getImages() == null
                                    ? 0
                                    : chapter.getImages().size()
                    );
                    item.put(
                            "moderationStatus",
                            chapter.getModerationStatus()
                    );
                    item.put("createdAt", chapter.getCreatedAt());
                    item.put("revision", revision);
                    item.put("canCreateTask", revision || (existing.isEmpty() && !isTeamTranslationPublished(chapter.getId(), team)));
                    item.put("resolutionNote", revision ? findLatestTranslationReportNote(chapter.getId(), team) : null);

                    return item;
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    private boolean isActiveTaskStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "backlog".equals(normalized)
                || "todo".equals(normalized)
                || "in_progress".equals(normalized)
                || "pending_review".equals(normalized)
                || "under_review".equals(normalized);
    }

    private int compareTaskRecency(TeamTaskEntity left, TeamTaskEntity right) {
        Instant leftCompleted = left.getCompletedAt();
        Instant rightCompleted = right.getCompletedAt();
        if (leftCompleted != null && rightCompleted != null) {
            return leftCompleted.compareTo(rightCompleted);
        }
        if (leftCompleted != null) return 1;
        if (rightCompleted != null) return -1;
        Instant leftSettled = left.getSettledAt();
        Instant rightSettled = right.getSettledAt();
        if (leftSettled != null && rightSettled != null) {
            return leftSettled.compareTo(rightSettled);
        }
        if (leftSettled != null) return 1;
        if (rightSettled != null) return -1;
        return 0;
    }

    private List<ChapterTranslationEntity> findTeamTranslations(UUID chapterId, ProjectTeamEntity team) {
        if (chapterId == null || team == null) {
            return List.of();
        }
        return chapterTranslationRepository.findByChapter_Id(chapterId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .filter(t -> team.getId().equals(t.getProjectTeamId()) || languageMatches(team, t.getLanguageCode()))
                .toList();
    }

    private boolean languageMatches(ProjectTeamEntity team, String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        String expected = (team.getTargetLang() == null || team.getTargetLang().isBlank())
                ? "vi"
                : team.getTargetLang().trim();
        return expected.equalsIgnoreCase(languageCode.trim());
    }

    private boolean isSupersededStatus(String status) {
        return status != null && "superseded".equalsIgnoreCase(status.trim());
    }

    private boolean canCreateRevisionTask(UUID chapterId, ProjectTeamEntity team) {
        if (chapterId == null || team == null) {
            return false;
        }
        List<TeamTaskEntity> existing = taskRepository.findByChapter_Id(chapterId).stream()
                .filter(t -> team.getId().equals(t.getProjectTeamId()))
                .filter(t -> !isSupersededStatus(t.getStatus()))
                .toList();
        return canCreateRevisionTask(chapterId, team, existing);
    }

    private boolean canCreateRevisionTask(UUID chapterId, ProjectTeamEntity team, List<TeamTaskEntity> existingTasks) {
        Optional<ReportEntity> acceptedReport = findLatestAcceptedTranslationReport(chapterId, team);
        if (acceptedReport.isEmpty()) {
            return false;
        }
        Instant resolvedAt = acceptedReport.get().getResolvedAt();
        return existingTasks.stream()
                .filter(t -> !isSupersededStatus(t.getStatus()))
                .noneMatch(t -> isTaskCreatedAfterAcceptedReport(t, resolvedAt));
    }

    private boolean isTaskCreatedAfterAcceptedReport(TeamTaskEntity task, Instant reportResolvedAt) {
        if (isActiveTaskStatus(task.getStatus()) && "REVISION".equalsIgnoreCase(task.getTaskType())) {
            return true;
        }
        if (isCompletedStatus(task.getStatus())
                && task.getCompletedAt() != null
                && reportResolvedAt != null
                && task.getCompletedAt().isAfter(reportResolvedAt)) {
            return true;
        }
        return false;
    }

    private boolean isTeamTranslationPublished(UUID chapterId, ProjectTeamEntity team) {
        return findTeamTranslations(chapterId, team).stream()
                .anyMatch(this::isPublishedTranslation);
    }

    private boolean isPublishedTranslation(ChapterTranslationEntity translation) {
        if (translation == null || Boolean.TRUE.equals(translation.getDeleted())) {
            return false;
        }
        return translation.getStatus() == null || translation.getStatus() == ChapterTranslationStatus.PUBLISHED;
    }

    private Optional<ReportEntity> findLatestAcceptedTranslationReport(UUID chapterId, ProjectTeamEntity team) {
        List<UUID> translationIds = findTeamTranslations(chapterId, team).stream()
                .map(ChapterTranslationEntity::getId)
                .toList();
        if (translationIds.isEmpty()) {
            return Optional.empty();
        }
        return reportRepository.findByTargetTypeAndTargetIdInAndStatusAndDeletedFalseOrderByResolvedAtDesc(
                        ReportTargetType.CHAPTER_TRANSLATIONS,
                        translationIds,
                        ReportStatus.ACCEPTED
                ).stream()
                .findFirst();
    }

    private String findLatestTranslationReportNote(UUID chapterId, ProjectTeamEntity team) {
        return findLatestAcceptedTranslationReport(chapterId, team)
                .map(ReportEntity::getResolutionNote)
                .filter(note -> note != null && !note.isBlank())
                .orElse(null);
    }

    private Map<Integer, String> collectPreviousTranslationBubbles(UUID chapterId, UUID teamId, ProjectTeamEntity team) {
        Map<Integer, String> pageBubblesMap = new HashMap<>();

        findTeamTranslations(chapterId, team).stream()
                .map(ChapterTranslationEntity::getPagesBubbles)
                .forEach(json -> mergeBubblesFromPagesBubblesJson(json, pageBubblesMap));

        List<TeamTaskEntity> previousTasks = taskRepository.findByChapter_Id(chapterId).stream()
                .filter(t -> teamId.equals(t.getProjectTeamId()))
                .sorted(this::compareTaskRecency)
                .toList();
        for (TeamTaskEntity prevTask : previousTasks) {
            List<PageTranslationEntity> prevPages = iPageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(prevTask.getId());
            for (PageTranslationEntity prevPage : prevPages) {
                if (hasTranslatedBubbles(prevPage.getBubbles())) {
                    pageBubblesMap.put(prevPage.getPageNumber(), prevPage.getBubbles());
                }
            }
        }
        return pageBubblesMap;
    }

    private void mergeBubblesFromPagesBubblesJson(String pagesBubbles, Map<Integer, String> pageBubblesMap) {
        if (pagesBubbles == null || pagesBubbles.isBlank()) {
            return;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(pagesBubbles);
            if (!rootNode.isArray()) {
                return;
            }
            for (JsonNode pageNode : rootNode) {
                int pageNumber = pageNode.path("pageNumber").asInt(pageNode.path("page_number").asInt(0));
                JsonNode bubblesNode = pageNode.path("bubbles");
                if (pageNumber <= 0 || bubblesNode == null || bubblesNode.isMissingNode() || bubblesNode.isNull()) {
                    continue;
                }
                String bubblesJson = bubblesNode.isTextual() ? bubblesNode.asText() : bubblesNode.toString();
                if (hasTranslatedBubbles(bubblesJson)) {
                    pageBubblesMap.putIfAbsent(pageNumber, bubblesJson);
                }
            }
        } catch (Exception ignored) {
            // Keep whatever bubbles were already recovered from previous tasks.
        }
    }

    private boolean hasTranslatedBubbles(String bubbles) {
        if (bubbles == null) {
            return false;
        }
        String trimmed = bubbles.trim();
        return !trimmed.isEmpty() && !"[]".equals(trimmed) && !"null".equals(trimmed);
    }

}