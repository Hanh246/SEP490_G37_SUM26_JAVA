package com.sep.comiverse.controller;

import com.sep.comiverse.entity.*;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/team-workspace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamWorkspaceController {

    private final ITeamAnnouncementRepository announcementRepository;
    private final ITeamMessageRepository messageRepository;
    private final ITeamTaskRepository taskRepository;
    private final ITeamJoinRequestRepository joinRequestRepository;

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
