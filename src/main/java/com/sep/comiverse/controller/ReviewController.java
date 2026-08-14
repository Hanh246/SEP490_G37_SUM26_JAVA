package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.dto.ReviewCommentDTO;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ReviewCommentEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IReviewCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.service.NotificationService;
import com.sep.comiverse.service.TeamTaskReviewService;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/review-workspace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final IPageTranslationRepository pageTranslationRepository;
    private final IReviewCommentRepository reviewCommentRepository;
    private final IUserRepository userRepository;
    private final ITeamTaskRepository taskRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final NotificationService notificationService;
    private final TeamTaskReviewService teamTaskReviewService;

    @GetMapping("/{taskId}")
    public ResponseEntity<List<ChapterPageDTO>> getPagesForReview(@PathVariable UUID taskId) {
        List<PageTranslationEntity> pages = pageTranslationRepository
                .findByTaskId_IdOrderByPageNumberAsc(taskId);

        List<ChapterPageDTO> result = pages.stream()
                .map(p -> ChapterPageDTO.builder()
                        .pageId(p.getId())
                        .pageNumber(p.getPageNumber())
                        .imageUrl(p.getImageUrl())
                        .status(p.getStatus())
                        .bubbles(p.getBubbles())
                        .reviewBaselineBubbles(p.getReviewBaselineBubbles())
                        .assignedTranslatorId(p.getAssignedTranslatorId())
                        .responsibilityFactor(p.getResponsibilityFactor())
                        .completedAt(p.getCompletedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/pages/{pageId}/comments")
    public ResponseEntity<List<ReviewCommentDTO>> getComments(@PathVariable UUID pageId) {
        List<ReviewCommentEntity> comments = reviewCommentRepository.findByPage_IdOrderByCreatedAtAsc(pageId);
        List<ReviewCommentDTO> result = comments.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pages/{pageId}/comments")
    public ResponseEntity<?> createComment(@PathVariable UUID pageId, @RequestBody Map<String, String> body) {
        UUID authorId = getCurrentUserId();
        if (authorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in."));
        }

        PageTranslationEntity page = pageTranslationRepository.findById(pageId).orElse(null);
        if (page == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Page not found"));
        }

        UserEntity author = userRepository.findById(authorId).orElse(null);
        if (author == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }

        String bubbleId = body.get("bubbleId");
        boolean isPageLevel = bubbleId == null || bubbleId.isBlank();

        String content = body.get("content");
        if (com.sep.comiverse.util.ProfanityFilterUtil.containsProfanity(content)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Your comment contains inappropriate language."));
        }

        boolean alreadyExists = isPageLevel
                ? reviewCommentRepository.findByPage_IdAndBubbleIdIsNullAndAuthor_Id(pageId, authorId).isPresent()
                : reviewCommentRepository.findByPage_IdAndBubbleIdAndAuthor_Id(pageId, bubbleId, authorId).isPresent();
        if (alreadyExists) {
            String message = isPageLevel
                    ? "You already left a page-level review. Edit your existing review instead."
                    : "You already reviewed this bubble. Edit your existing review instead.";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", message));
        }

        ReviewCommentEntity comment = ReviewCommentEntity.builder()
                .page(page)
                .bubbleId(isPageLevel ? null : bubbleId)
                .author(author)
                .content(body.get("content"))
                .resolved(false)
                .build();

        ReviewCommentEntity created = reviewCommentRepository.save(comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> updateComment(@PathVariable UUID commentId, @RequestBody Map<String, String> body) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in."));
        }

        ReviewCommentEntity comment = reviewCommentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Comment not found"));
        }

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You can only edit your own comments."));
        }

        comment.setContent(body.get("content"));
        ReviewCommentEntity updated = reviewCommentRepository.save(comment);
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable UUID commentId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "You must be logged in."));
        }

        ReviewCommentEntity comment = reviewCommentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Comment not found"));
        }

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You can only delete your own comments."));
        }

        reviewCommentRepository.deleteById(commentId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/comments/{commentId}/resolve")
    public ResponseEntity<?> resolveComment(@PathVariable UUID commentId) {
        ReviewCommentEntity comment = reviewCommentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Comment not found"));
        }
        comment.setResolved(true);
        reviewCommentRepository.save(comment);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/tasks/{taskId}/decision")
    @Transactional
    @PreAuthorize("hasAuthority('PROJECT_LEADER')")
    public ResponseEntity<?> submitDecision(
            @PathVariable UUID taskId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String decision = body.get("decision");
        log.info("[submitDecision] taskId={} decision={}", taskId, decision);

        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("[submitDecision] task not found for taskId={}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Task not found"));
        }
        ProjectTeamEntity taskTeam = projectTeamRepository.findById(task.getProjectTeamId()).orElse(null);
        boolean ownsTeam = principal != null && taskTeam != null && (
                (taskTeam.getLeaderId() != null && principal.getId().equals(taskTeam.getLeaderId()))
                        || (taskTeam.getLeaderId() == null && taskTeam.getLeaderName() != null
                        && (taskTeam.getLeaderName().equalsIgnoreCase(principal.getUsername())
                        || (principal.getFullName() != null
                        && taskTeam.getLeaderName().equalsIgnoreCase(principal.getFullName()))))
        );
        if (!ownsTeam) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Only this team's Project Leader can review and complete the task"));
        }

        if ("approved".equals(decision)) {
            try {
                teamTaskReviewService.approveAndPublish(task, principal.getId());
            } catch (CustomException ex) {
                return ResponseEntity.status(ex.getHttpStatus())
                        .body(Map.of("success", false, "message", ex.getMessage()));
            }
        } else if ("changes_requested".equals(decision)) {
            teamTaskReviewService.returnToInProgress(task);
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid decision: " + decision));
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    private ReviewCommentDTO toDto(ReviewCommentEntity c) {
        return ReviewCommentDTO.builder()
                .id(c.getId())
                .bubbleId(c.getBubbleId())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFullName())
                .authorInitials(computeInitials(c.getAuthor().getFullName()))
                .content(c.getContent())
                .resolved(c.getResolved())
                .createdAt(c.getCreatedAt())
                .build();
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

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String principalName = authentication.getName();
        UserEntity user = userRepository.findByEmail(principalName)
                .orElseGet(() -> userRepository.findByUsername(principalName).orElse(null));
        return user != null ? user.getId() : null;
    }
}