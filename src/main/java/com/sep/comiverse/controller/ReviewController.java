package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.dto.ReviewCommentDTO;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ReviewCommentEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IReviewCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/review-workspace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final IPageTranslationRepository pageTranslationRepository;
    private final IReviewCommentRepository reviewCommentRepository;
    private final IUserRepository userRepository;
    private final ITeamTaskRepository taskRepository;

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

        if (bubbleId != null && !bubbleId.isBlank()) {
            boolean alreadyExists = reviewCommentRepository.findByPage_IdAndBubbleId(pageId, bubbleId).isPresent();
            if (alreadyExists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false, "message", "This bubble already has a comment. Edit it instead."));
            }
        }

        ReviewCommentEntity comment = ReviewCommentEntity.builder()
                .page(page)
                .bubbleId(bubbleId)
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
    public ResponseEntity<?> submitDecision(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        String decision = body.get("decision");

        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Task not found"));
        }

        if ("approved".equals(decision)) {
            task.setStatus("completed");
            List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);
            for (PageTranslationEntity page : pages) {
                page.setReviewBaselineBubbles(page.getBubbles());
            }
            pageTranslationRepository.saveAll(pages);
        } else if ("changes_requested".equals(decision)) {
            task.setStatus("in_progress");
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid decision: " + decision));
        }

        taskRepository.save(task);
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