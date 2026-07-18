package com.sep.comiverse.controller;

import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sep.comiverse.entity.enums.ChapterStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                .map(TeamTaskEntity::getChapterId)
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
    public ResponseEntity<TeamTaskEntity> createTask(@PathVariable UUID teamId, @RequestBody TeamTaskEntity task) {
        task.setProjectTeamId(teamId);
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        return ResponseEntity.ok(taskRepository.save(task));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<TeamTaskEntity> updateTask(@PathVariable UUID id, @RequestBody TeamTaskEntity taskUpdates) {
        return taskRepository.findById(id).map(task -> {
            if (taskUpdates.getColumnName() != null) {
                task.setColumnName(taskUpdates.getColumnName());
            }
            if (taskUpdates.getProgress() != null) {
                task.setProgress(taskUpdates.getProgress());
            }
            return ResponseEntity.ok(taskRepository.save(task));
        }).orElse(ResponseEntity.notFound().build());
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

    @GetMapping("/requests/by-name")
    public ResponseEntity<List<TeamJoinRequestEntity>> getRequestsByName(@RequestParam String name) {
        return ResponseEntity.ok(joinRequestRepository.findByName(name));
    }

    @PostMapping("/{teamId}/requests")
    public ResponseEntity<?> createRequest(@PathVariable UUID teamId, @RequestBody TeamJoinRequestEntity request) {
        if (request.getName() != null && joinRequestRepository.existsByNameAndProjectTeamId(request.getName(), teamId)) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "You have already applied to this team!"));
        }
        request.setProjectTeamId(teamId);
        return ResponseEntity.ok(joinRequestRepository.save(request));
    }

    @PutMapping("/requests/{id}/decision")
    public ResponseEntity<Void> decideRequest(@PathVariable UUID id, @RequestBody java.util.Map<String, String> body) {
        String decision = body.get("decision");
        return joinRequestRepository.findById(id).map(req -> {
            if ("approved".equalsIgnoreCase(decision)) {
                projectTeamRepository.findById(req.getProjectTeamId()).ifPresent(team -> {
                    if (team.getMembersCount() == null) {
                        team.setMembersCount(1);
                    }
                    team.setMembersCount(team.getMembersCount() + 1);
                    projectTeamRepository.save(team);
                });
            }
            joinRequestRepository.deleteById(id);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
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
