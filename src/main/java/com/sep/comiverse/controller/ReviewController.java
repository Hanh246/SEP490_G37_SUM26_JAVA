package com.sep.comiverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.dto.ReviewCommentDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.ReviewCommentEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IReviewCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final IChapterRepository chapterRepository;
    private final com.sep.comiverse.repository.IComicRepository comicRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        boolean isPageLevel = bubbleId == null || bubbleId.isBlank();

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
    public ResponseEntity<?> submitDecision(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        String decision = body.get("decision");
        log.info("[submitDecision] taskId={} decision={}", taskId, decision);

        TeamTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("[submitDecision] task not found for taskId={}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Task not found"));
        }

        if ("approved".equals(decision)) {
            task.setStatus("completed");

            List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);
            log.info("[submitDecision] found {} PageTranslationEntity rows for taskId={}", pages.size(), taskId);
            for (PageTranslationEntity page : pages) {
                page.setReviewBaselineBubbles(page.getBubbles());
            }
            pageTranslationRepository.saveAll(pages);

            try {
                publishChapterFromTask(task, pages);
            } catch (Exception ex) {
                log.error("[submitDecision] Failed to publish chapter for taskId={}", taskId, ex);
            }
        } else if ("changes_requested".equals(decision)) {
            task.setStatus("in_progress");
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid decision: " + decision));
        }

        taskRepository.save(task);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void publishChapterFromTask(TeamTaskEntity task, List<PageTranslationEntity> pages) {
        ChapterEntity chapter = task.getChapter();
        if (chapter == null) {
            log.warn("[publishChapterFromTask] task {} has no chapter linked, skipping publish", task.getId());
            return;
        }

        chapter.setModerationStatus(ChapterStatus.PUBLISHED);
        ChapterEntity savedChapter = chapterRepository.save(chapter);

        ProjectTeamEntity team = projectTeamRepository.findById(task.getProjectTeamId()).orElse(null);
        if (team != null) {
            team.setChaptersCount(team.getChaptersCount() == null ? 1 : team.getChaptersCount() + 1);
            projectTeamRepository.save(team);
        }

        ComicEntity comic = savedChapter.getComic();
        if (comic != null) {
            comic.setLatestChapterNumber(savedChapter.getChapterNumber());
            comic.setLastChapterUpdatedAt(Instant.now());
            long publishedCount = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                    comic.getId(), ChapterStatus.PUBLISHED);
            comic.setChapterCount(publishedCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) publishedCount);
            comicRepository.save(comic);
        }

        // ── Publish bản dịch vào chapter_translations ──────────────────────
        String languageCode = (team != null && team.getTargetLang() != null)
                ? team.getTargetLang()
                : "vi"; // fallback mặc định
        String pagesBubblesJson = buildPagesBubblesJson(pages);
        UUID teamId = task.getProjectTeamId();

        // Upsert: nếu đã tồn tại bản ghi cùng chapter + languageCode thì update, ngược lại tạo mới
        ChapterTranslationEntity translation = chapterTranslationRepository
                .findByChapter_IdAndLanguageCode(savedChapter.getId(), languageCode)
                .orElseGet(ChapterTranslationEntity::new);

        translation.setChapter(savedChapter);
        translation.setLanguageCode(languageCode);
        translation.setPagesBubbles(pagesBubblesJson);
        translation.setProjectTeamId(teamId);
        chapterTranslationRepository.save(translation);
        log.info("[publishChapterFromTask] Saved ChapterTranslationEntity for chapterId={} languageCode={} teamId={}",
                savedChapter.getId(), languageCode, teamId);
    }

    private String buildPagesBubblesJson(List<PageTranslationEntity> pages) {
        try {
            List<Map<String, Object>> pagePayload = new ArrayList<>();
            for (PageTranslationEntity page : pages) {
                Map<String, Object> pageMap = new HashMap<>();
                pageMap.put("pageId", page.getId());
                pageMap.put("pageNumber", page.getPageNumber());
                pageMap.put("imageUrl", page.getImageUrl());
                pageMap.put("bubbles", page.getBubbles());
                pagePayload.add(pageMap);
            }
            return objectMapper.writeValueAsString(pagePayload);
        } catch (Exception ex) {
            log.error("[buildPagesBubblesJson] Failed to serialize pages", ex);
            return "[]";
        }
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