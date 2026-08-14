package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.entity.enums.PageStatus;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.TranslatorPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/translate-workspace")
@RequiredArgsConstructor
public class PageController {
    private final IPageTranslationRepository pageTranslationRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;
    private final TranslatorPaymentService translatorPaymentService;

    @GetMapping("/{taskId}")
    public ResponseEntity<?> getPagesForTask(@PathVariable UUID taskId) {
        List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);
        return ResponseEntity.ok(pages.stream().map(this::toPageDetailDto).toList());
    }

    @PutMapping("/pages/{pageId}/bubbles")
    public ResponseEntity<?> saveBubbles(
            @PathVariable UUID pageId,
            @RequestBody ChapterPageDTO request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageTranslationEntity page = pageTranslationRepository.findById(pageId).orElse(null);
        if (page == null) return ResponseEntity.notFound().build();
        if (!canEditPage(principal, page)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You are not assigned to this page"));
        }
        if (isLockedForReview(page.getTaskId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "Pages cannot be edited while the task is under review or completed"));
        }

        String bubbles = request.getBubbles() != null ? request.getBubbles() : "[]";
        page.setBubbles(bubbles);
        if (hasTranslationWork(bubbles)) {
            page.setStatus(PageStatus.DONE);
            page.setCompletedAt(Instant.now());
            if (principal != null && principal.getId() != null) {
                page.setAssignedTranslatorId(principal.getId());
            }
        } else {
            page.setStatus(PageStatus.TODO);
            page.setCompletedAt(null);
        }
        pageTranslationRepository.save(page);
        return ResponseEntity.ok(toPageDetailDto(page));
    }

    @PutMapping("/pages/{pageId}/status")
    public ResponseEntity<?> updatePageStatus(
            @PathVariable UUID pageId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageTranslationEntity page = pageTranslationRepository.findById(pageId).orElse(null);
        if (page == null) return ResponseEntity.notFound().build();
        if (!canEditPage(principal, page)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You are not assigned to this page"));
        }
        if (isLockedForReview(page.getTaskId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "Page status cannot be changed while the task is under review or completed"));
        }

        PageStatus status;
        try {
            status = PageStatus.valueOf(String.valueOf(body.get("status")).trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Status must be TODO or DONE"));
        }
        return ResponseEntity.ok(toPageDetailDto(translatorPaymentService.updatePageStatus(page, status)));
    }

    private boolean hasTranslationWork(String bubbles) {
        if (bubbles == null) return false;
        String trimmed = bubbles.trim();
        if (trimmed.isEmpty() || "[]".equals(trimmed) || "{}".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if (trimmed.contains("\"selections\"")) {
            return trimmed.matches("(?s).*\"selections\"\\s*:\\s*\\[\\s*\\{.*");
        }
        return trimmed.startsWith("[") && trimmed.contains("{");
    }

    private ChapterPageDTO toPageDetailDto(PageTranslationEntity page) {
        return ChapterPageDTO.builder()
                .pageId(page.getId())
                .pageNumber(page.getPageNumber())
                .imageUrl(page.getImageUrl())
                .status(page.getStatus())
                .bubbles(page.getBubbles())
                .reviewBaselineBubbles(page.getReviewBaselineBubbles())
                .assignedTranslatorId(page.getAssignedTranslatorId())
                .responsibilityFactor(page.getResponsibilityFactor())
                .completedAt(page.getCompletedAt())
                .build();
    }

    private boolean canEditPage(UserPrincipal principal, PageTranslationEntity page) {
        if (principal == null || principal.getId() == null || page == null) return false;
        if (principal.getId().equals(page.getAssignedTranslatorId())) return true;
        if (!"PROJECT_LEADER".equalsIgnoreCase(principal.getRole()) || page.getTaskId() == null) return false;
        ProjectTeamEntity team = projectTeamRepository.findById(page.getTaskId().getProjectTeamId()).orElse(null);
        return team != null && principal.getId().equals(team.getLeaderId());
    }

    private boolean isLockedForReview(TeamTaskEntity task) {
        if (task == null) return false;
        String status = task.getStatus();
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        boolean lockedStatus = normalized.equals("under_review")
                || normalized.equals("completed")
                || normalized.equals("published");
        if (!lockedStatus) {
            return false;
        }
        return !hasUnpublishedTranslation(task);
    }

    private boolean hasUnpublishedTranslation(TeamTaskEntity task) {
        if (task.getChapter() == null || task.getProjectTeamId() == null) {
            return false;
        }
        return chapterTranslationRepository.findByChapter_Id(task.getChapter().getId()).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .filter(t -> task.getProjectTeamId().equals(t.getProjectTeamId())
                        || t.getStatus() == ChapterTranslationStatus.UNPUBLISHED)
                .anyMatch(t -> t.getStatus() == ChapterTranslationStatus.UNPUBLISHED);
    }
}
