package com.sep.comiverse.controller;

import com.sep.comiverse.dto.SubmissionDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.plugin.crud.SubmissionCrudPlugin;
import com.sep.comiverse.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.sep.comiverse.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/submissions")
public class SubmissionController extends BaseController<SubmissionEntity, SubmissionDTO, UUID, PaginationSearchDTO> {

    @Autowired
    private ISubmissionRepository submissionRepository;

    @Autowired
    private com.sep.comiverse.service.AuditLogService auditLogService;

    @Autowired
    private IComicRepository comicRepository;

    @Autowired
    private IProjectTeamRepository projectTeamRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private com.sep.comiverse.service.AuthorComicService authorComicService;

    @Autowired
    private com.sep.comiverse.plugin.crud.ChapterCrudPlugin chapterCrudPlugin;

    @Autowired
    private com.sep.comiverse.service.NotificationService notificationService;

    private static final Pattern CHAPTER_NUMBER_PATTERN =
            Pattern.compile("(?i)chapter\\s+([0-9]+(?:[,.][0-9]+)?)");

    @Autowired
    public SubmissionController(SubmissionCrudPlugin crud) {
        super(crud, SubmissionEntity.class);
    }

    @GetMapping("/all")
    @Transactional
    public ResponseEntity<BaseResponse<List<SubmissionDTO>>> listAll(org.springframework.security.core.Authentication authentication) {
        List<SubmissionDTO> all = crudPlugin.listAll();

        // ── Self-healing data reconciliation ──
        // Fix stale submissions where chapter is already PUBLISHED but submission is still PENDING.
        // This auto-corrects data inconsistency from past approvals that only updated the chapters table.
        List<SubmissionDTO> reconciled = new java.util.ArrayList<>();
        for (SubmissionDTO dto : all) {
            if ("pending".equalsIgnoreCase(dto.getStatus()) && dto.getChapterId() != null) {
                ChapterEntity linkedChapter = chapterRepository.findById(dto.getChapterId()).orElse(null);
                if (linkedChapter != null && linkedChapter.getModerationStatus() == ChapterStatus.PUBLISHED) {
                    // Chapter already published — auto-fix this submission to 'approved'
                    SubmissionEntity staleSubmission = submissionRepository.findById(dto.getId()).orElse(null);
                    if (staleSubmission != null) {
                        staleSubmission.setStatus("approved");
                        submissionRepository.save(staleSubmission);
                        dto.setStatus("approved");
                    }
                }
            }
            reconciled.add(dto);
        }
        all = reconciled;
        
        if (authentication != null && authentication.getPrincipal() instanceof com.sep.comiverse.security.UserPrincipal principal) {
            com.sep.comiverse.entity.UserEntity user = principal.user();
            if ("MODERATOR".equalsIgnoreCase(user.getRole().getRoleName())) {
                String langs = user.getAssignedLanguages();
                if (langs != null && !langs.isBlank()) {
                    List<String> scope = java.util.Arrays.stream(langs.toLowerCase().split(","))
                            .map(String::trim).toList();
                    all = all.stream().filter(s -> {
                        String comicLang = s.getLanguage();
                        if (comicLang == null) return false;
                        return scope.stream().anyMatch(l -> comicLang.toLowerCase().contains(l) || l.contains(comicLang.toLowerCase()));
                    }).toList();
                } else {
                    all = java.util.Collections.emptyList();
                }
            }
        }

        return ResponseEntity.ok(BaseResponse.<List<SubmissionDTO>>builder()
                .success(true)
                .data(all)
                .build());
    }

    // ── CLAIM TICKET MECHANISM ─────────────────────────────────────────

    /** How long a claim stays valid before it auto-expires (30 minutes). */
    private static final long CLAIM_EXPIRY_MINUTES = 30;

    /**
     * Claim a submission for review (IN_REVIEW).
     * Uses PESSIMISTIC_WRITE lock to prevent two moderators from claiming simultaneously.
     */
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> claim(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubmissionEntity submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission " + id + " not found"));

        // Only pending submissions can be claimed
        if (!"pending".equalsIgnoreCase(submission.getStatus())) {
            return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                    .success(true)
                    .message("Submission is already " + submission.getStatus())
                    .data(crudPlugin.getPlugin().toDto(submission))
                    .build());
        }

        UUID currentUserId = principal != null ? principal.getId() : null;

        // Check if already claimed by someone else (and not expired)
        if (submission.getReviewerId() != null
                && !submission.getReviewerId().equals(currentUserId)
                && submission.getReviewStartedAt() != null
                && java.time.Duration.between(submission.getReviewStartedAt(), java.time.Instant.now()).toMinutes() < CLAIM_EXPIRY_MINUTES) {

            // Resolve the reviewer name for the error message
            String reviewerName = "another moderator";
            if (submission.getReviewerId() != null) {
                var reviewer = userRepository.findById(submission.getReviewerId()).orElse(null);
                if (reviewer != null) {
                    reviewerName = reviewer.getFullName() != null ? reviewer.getFullName() : reviewer.getUsername();
                }
            }

            return ResponseEntity.status(409).body(BaseResponse.<SubmissionDTO>builder()
                    .success(false)
                    .message("This submission is currently being reviewed by " + reviewerName)
                    .data(crudPlugin.getPlugin().toDto(submission))
                    .build());
        }

        // Claim it
        submission.setReviewerId(currentUserId);
        submission.setReviewStartedAt(java.time.Instant.now());
        submissionRepository.save(submission);

        auditLogService.log("REVIEW_QUEUE", "Claimed submission: " + submission.getTitle());

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .message("Submission claimed for review")
                .data(crudPlugin.getPlugin().toDto(submission))
                .build());
    }

    /**
     * Release a claimed submission (back to PENDING, no reviewer).
     */
    @PutMapping("/{id}/release")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> release(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubmissionEntity submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission " + id + " not found"));

        UUID currentUserId = principal != null ? principal.getId() : null;

        // Only the claimer (or admin) can release
        if (submission.getReviewerId() != null
                && !submission.getReviewerId().equals(currentUserId)
                && (principal == null || !principal.user().getRole().getRoleName().equalsIgnoreCase("ADMIN"))) {
            return ResponseEntity.status(403).body(BaseResponse.<SubmissionDTO>builder()
                    .success(false)
                    .message("Only the claiming moderator or an admin can release this submission")
                    .build());
        }

        submission.setReviewerId(null);
        submission.setReviewStartedAt(null);
        submissionRepository.save(submission);

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .message("Submission released")
                .data(crudPlugin.getPlugin().toDto(submission))
                .build());
    }

    // ── APPROVE / REJECT (with Pessimistic Lock) ───────────────────────

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        // Use pessimistic lock to prevent concurrent approve
        SubmissionEntity submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        // Guard: if someone else has claimed it and claim is still valid, reject the action
        UUID currentUserId = principal != null ? principal.getId() : null;
        if (submission.getReviewerId() != null
                && !submission.getReviewerId().equals(currentUserId)
                && submission.getReviewStartedAt() != null
                && java.time.Duration.between(submission.getReviewStartedAt(), java.time.Instant.now()).toMinutes() < CLAIM_EXPIRY_MINUTES) {
            return ResponseEntity.status(409).body(BaseResponse.<SubmissionDTO>builder()
                    .success(false)
                    .message("This submission is being reviewed by another moderator. Please wait or choose a different submission.")
                    .data(crudPlugin.getPlugin().toDto(submission))
                    .build());
        }

        boolean alreadyApproved = "approved".equalsIgnoreCase(submission.getStatus());
        submission.setStatus("approved");
        // Clear claim after action
        submission.setReviewerId(null);
        submission.setReviewStartedAt(null);
        UUID modId = null;
        if (principal != null) {
            submission.setModeratorId(principal.getId());
            modId = principal.getId();
        }
        SubmissionEntity savedSubmission = submissionRepository.save(submission);

        if (!alreadyApproved) {
            String targetDesc = (submission.getChapter() != null && !submission.getChapter().isBlank())
                    ? "chapter " + submission.getChapter()
                    : "Comic profile";
            auditLogService.log("REVIEW_QUEUE", "Approved " + targetDesc + " of " + submission.getTitle());
            if ("author".equalsIgnoreCase(submission.getQueueType())) {
                handleAuthorApproval(submission, modId);
            }
            ComicEntity comic = resolveComic(submission);
            if (comic != null) {
                chapterCrudPlugin.evictChaptersCache(comic.getId());
            }
            notifySubmissionOwner(submission, true, null);
        }

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }

    private void handleAuthorApproval(SubmissionEntity submission, UUID modId) {
        if (submission == null) {
            return;
        }

        /*
         * Case 1: Author submit chapter review.
         * Khi moderator approve chapter submission:
         * - lấy đúng ChapterEntity theo submission.chapterId
         * - set moderationStatus = PUBLISHED
         * - cập nhật metadata comic theo chapter đã publish
         */
        if (submission.getChapterId() != null) {
            ChapterEntity chapter = chapterRepository.findById(submission.getChapterId())
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Chapter with id " + submission.getChapterId() + " not found"
                    ));

            chapter.setModerationStatus(ChapterStatus.PUBLISHED);
            chapter.setApprovedById(modId);
            chapter.setApprovedAt(java.time.Instant.now());
            chapter.setRejectionReason(null);
            chapter.setRejectedById(null);
            ChapterEntity savedChapter = chapterRepository.save(chapter);

            ComicEntity comic = null;
            if (savedChapter.getComic() != null) {
                comic = comicRepository.findById(savedChapter.getComic().getId()).orElse(null);
            }
            if (comic != null) {
                if (comic.getModerationStatus() != ComicModerationStatus.PUBLISHED) {
                    authorComicService.assertPublishedComicQuotaAvailable(comic);
                    comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                    comic.setApprovedById(modId);
                    comic.setApprovedAt(java.time.Instant.now());
                }
                refreshComicMetadataAfterPublishedChapter(comic, savedChapter);
                comicRepository.save(comic);
                
                notifyTeamOfNewChapters(comic, savedChapter.getTitle());
            }

            return;
        }

        /*
         * Case 2: Author submit comic profile/review.
         * Khi moderator approve comic submission:
         * - lấy đúng ComicEntity theo submission.comicId
         * - set moderationStatus = PUBLISHED
         * - publish các chapter đi kèm nếu có
         */
        if (submission.getComicId() != null) {
            ComicEntity comic = comicRepository.findById(submission.getComicId())
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Comic with id " + submission.getComicId() + " not found"
                    ));

            if (comic.getModerationStatus() != ComicModerationStatus.PUBLISHED) {
                authorComicService.assertPublishedComicQuotaAvailable(comic);
                comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
            }
            List<ChapterEntity> comicChapters = chapterRepository.findAllByComic_IdAndDeletedFalse(comic.getId());
            boolean anyNewChapterPublished = false;
            for (ChapterEntity ch : comicChapters) {
                if (ch.getModerationStatus() != ChapterStatus.PUBLISHED) {
                    ch.setModerationStatus(ChapterStatus.PUBLISHED);
                    ch.setApprovedById(modId);
                    ch.setApprovedAt(java.time.Instant.now());
                    ch.setRejectionReason(null);
                    ch.setRejectedById(null);
                    chapterRepository.save(ch);
                    anyNewChapterPublished = true;
                }
            }
            
            if (anyNewChapterPublished) {
                notifyTeamOfNewChapters(comic, "Multiple chapters");
            }
            
            if (!comicChapters.isEmpty()) {
                refreshComicMetadataAfterPublishedChapter(comic, comicChapters.get(0));
            }
            comicRepository.save(comic);

            return;
        }

        /*
         * Backward-compatible path:
         * Chỉ dùng cho submission cũ chưa có comicId/chapterId.
         * Không nên là luồng chính nữa.
         */
        ComicEntity comic = resolveComic(submission);
        if (comic != null) {
            if (comic.getModerationStatus() != ComicModerationStatus.PUBLISHED) {
                authorComicService.assertPublishedComicQuotaAvailable(comic);
                comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
            }
            List<ChapterEntity> comicChapters = chapterRepository.findAllByComic_IdAndDeletedFalse(comic.getId());
            for (ChapterEntity ch : comicChapters) {
                if (ch.getModerationStatus() != ChapterStatus.PUBLISHED) {
                    ch.setModerationStatus(ChapterStatus.PUBLISHED);
                    ch.setApprovedById(modId);
                    ch.setApprovedAt(java.time.Instant.now());
                    ch.setRejectionReason(null);
                    ch.setRejectedById(null);
                    chapterRepository.save(ch);
                }
            }
            if (!comicChapters.isEmpty()) {
                refreshComicMetadataAfterPublishedChapter(comic, comicChapters.get(0));
            }
            comicRepository.save(comic);
        }
    }
    private void refreshComicMetadataAfterPublishedChapter(ComicEntity comic, ChapterEntity chapter) {
        if (comic == null || chapter == null) {
            return;
        }

        comic.setLatestChapterNumber(chapter.getChapterNumber());
        comic.setLastChapterUpdatedAt(Instant.now());

        long publishedChapterCount = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                comic.getId(),
                ChapterStatus.PUBLISHED
        );

        comic.setChapterCount(
                publishedChapterCount > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int) publishedChapterCount
        );
    }

    private ComicEntity resolveComic(SubmissionEntity submission) {
        if (submission.getComicId() != null) {
            return comicRepository.findById(submission.getComicId()).orElse(null);
        }
        if (submission.getTitle() != null) {
            return comicRepository.findByTitle(submission.getTitle()).orElse(null);
        }
        return null;
    }

    private void updateLatestChapterIfAuthorChapterSubmission(ComicEntity comic, SubmissionEntity submission) {
        if (comic == null || submission == null) {
            return;
        }
        if (submission.getChapterId() == null && !looksLikeChapterSubmission(submission.getChapter())) {
            return;
        }
        String chapterNumber = extractChapterNumber(submission.getChapter());
        if (chapterNumber != null) {
            comic.setLatestChapterNumber(chapterNumber);
        }
        comic.setLastChapterUpdatedAt(Instant.now());
        long chapterCount = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(comic.getId(), ChapterStatus.PUBLISHED);
        comic.setChapterCount(chapterCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) chapterCount);

        List<ChapterEntity> publishedChapters = chapterRepository
                .findAllByComic_IdAndDeletedFalseAndModerationStatus(comic.getId(), ChapterStatus.PUBLISHED);

        comic.setChapterCount(publishedChapters.size());
        publishedChapters.stream()
                .filter(chapter -> chapter.getChapterNumber() != null && !chapter.getChapterNumber().isBlank())
                .max(java.util.Comparator.comparing(chapter -> toChapterSortNumber(chapter.getChapterNumber())))
                .ifPresentOrElse(chapter -> {
                    comic.setLatestChapterNumber(chapter.getChapterNumber());
                    comic.setLastChapterUpdatedAt(Instant.now());
                }, () -> {
                    comic.setLatestChapterNumber(null);
                    comic.setLastChapterUpdatedAt(Instant.now());
                });
    }

    private BigDecimal toChapterSortNumber(String chapterNumber) {
        try {
            return new BigDecimal(chapterNumber.replace(',', '.'));
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private boolean looksLikeChapterSubmission(String chapter) {
        return chapter != null && CHAPTER_NUMBER_PATTERN.matcher(chapter).find();
    }

    private String extractChapterNumber(String chapter) {
        if (chapter == null) {
            return null;
        }

        Matcher matcher = CHAPTER_NUMBER_PATTERN.matcher(chapter);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1).replace(',', '.');
    }

    /**
     * Handles side-effects of rejecting a submission.
     * @return true if the comic profile was auto-rejected because all chapters are now rejected.
     */
    private boolean handleSubmissionRejected(SubmissionEntity submission, UUID modId) {
        if (submission == null || !"author".equalsIgnoreCase(submission.getQueueType())) {
            return false;
        }
        if (submission.getChapterId() != null) {
            chapterRepository.findById(submission.getChapterId()).ifPresent(chapter -> {
                // If the chapter was already rejected (e.g. via direct chapter API), preserve its specific reason
                if (chapter.getModerationStatus() != ChapterStatus.REJECTED) {
                    chapter.setRejectionReason(submission.getRejectionReason());
                }
                chapter.setModerationStatus(ChapterStatus.REJECTED);
                chapter.setRejectedById(modId);

                // Preserve rejected chapter evidence. If an older code path already
                // cleared chapters.images before this transaction, recover the URL
                // list from the immutable submission snapshot captured at submit time.
                if ((chapter.getImages() == null || chapter.getImages().isEmpty())
                        && submission.getChapterImages() != null
                        && !submission.getChapterImages().isEmpty()) {
                    chapter.setImages(new java.util.ArrayList<>(submission.getChapterImages()));
                }

                // Save a snapshot of the images at the time of rejection
                if (chapter.getImages() != null && !chapter.getImages().isEmpty()) {
                    chapter.setRejectedImagesSnapshot(new java.util.ArrayList<>(chapter.getImages()));
                }

                chapterRepository.save(chapter);
            });

            // Auto-reject the comic profile only when every active chapter is really
            // REJECTED. Do not mix legacy PENDING_REVIEW with SUBMITTED_FOR_REVIEW;
            // that mismatch used to auto-reject comics while another chapter was still pending.
            if (submission.getComicId() != null) {
                java.util.List<ChapterEntity> activeChapters =
                        chapterRepository.findAllByComic_IdAndDeletedFalse(submission.getComicId());
                boolean allChaptersRejected = !activeChapters.isEmpty()
                        && activeChapters.stream().allMatch(ch -> ch.getModerationStatus() == ChapterStatus.REJECTED);

                if (allChaptersRejected) {
                    // All chapters are rejected — auto-reject the comic profile submission
                    String autoReason = "All chapters were rejected. Comic profile auto-rejected.";

                    submissionRepository.findTopByComicIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                            submission.getComicId(), "author", "pending"
                    ).ifPresent(comicSub -> {
                        comicSub.setStatus("rejected");
                        comicSub.setRejectionReason(autoReason);
                        comicSub.setModeratorId(submission.getModeratorId());
                        submissionRepository.save(comicSub);
                    });

                    comicRepository.findById(submission.getComicId()).ifPresent(comic -> {
                        comic.setModerationStatus(ComicModerationStatus.REJECTED);
                        comic.setRejectionReason(autoReason);
                        comicRepository.save(comic);
                    });

                    // Notify the author
                    notifySubmissionOwner(submission, false, autoReason);

                    return true;
                }
            }
            return false;
        }
        if (submission.getComicId() != null) {
            comicRepository.findById(submission.getComicId()).ifPresent(comic -> {
                comic.setModerationStatus(ComicModerationStatus.REJECTED);
                comic.setRejectionReason(submission.getRejectionReason());
                comicRepository.save(comic);
                
                // Keep rejected chapter page URLs as moderation evidence until the author
                // replaces the folder with a corrected version.
                java.util.List<ChapterEntity> chapters = chapterRepository.findAllByComic_IdAndDeletedFalse(comic.getId());
                for (ChapterEntity ch : chapters) {
                    if (ch.getModerationStatus() != ChapterStatus.PUBLISHED) {
                        // Only overwrite the reason if it hasn't been explicitly rejected with a specific reason before
                        if (ch.getModerationStatus() != ChapterStatus.REJECTED) {
                            ch.setRejectionReason(submission.getRejectionReason());
                        }
                        ch.setModerationStatus(ChapterStatus.REJECTED);
                        ch.setRejectedById(modId);
                        if (ch.getImages() != null && !ch.getImages().isEmpty()) {
                            ch.setRejectedImagesSnapshot(new java.util.ArrayList<>(ch.getImages()));
                        }
                        chapterRepository.save(ch);
                    }
                }
            });
        }
        return false;
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        // Use pessimistic lock to prevent concurrent reject
        SubmissionEntity submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        // Guard: if someone else has claimed it and claim is still valid, reject the action
        UUID currentUserId = principal != null ? principal.getId() : null;
        if (submission.getReviewerId() != null
                && !submission.getReviewerId().equals(currentUserId)
                && submission.getReviewStartedAt() != null
                && java.time.Duration.between(submission.getReviewStartedAt(), java.time.Instant.now()).toMinutes() < CLAIM_EXPIRY_MINUTES) {
            return ResponseEntity.status(409).body(BaseResponse.<SubmissionDTO>builder()
                    .success(false)
                    .message("This submission is being reviewed by another moderator.")
                    .data(crudPlugin.getPlugin().toDto(submission))
                    .build());
        }

        String reason = body != null ? body.getOrDefault("reason", "No reason provided.") : "No reason provided.";
        boolean alreadyRejected = "rejected".equalsIgnoreCase(submission.getStatus());
        submission.setStatus("rejected");
        submission.setRejectionReason(reason);
        // Clear claim after action
        submission.setReviewerId(null);
        submission.setReviewStartedAt(null);
        if (principal != null) {
            submission.setModeratorId(principal.getId());
        }

        SubmissionEntity savedSubmission = submissionRepository.save(submission);
        boolean comicAutoRejected = false;
        if (!alreadyRejected) {
            String targetDesc = "author".equalsIgnoreCase(submission.getQueueType()) ? "Comic profile" : "chapter " + submission.getChapter();
            auditLogService.log("REVIEW_QUEUE", "Rejected " + targetDesc + " of " + submission.getTitle() + " (Reason: " + reason + ")");
            UUID modId = principal != null ? principal.getId() : null;
            comicAutoRejected = handleSubmissionRejected(submission, modId);
            notifySubmissionOwner(submission, false, reason);
            if (comicAutoRejected) {
                auditLogService.log("REVIEW_QUEUE", "Auto-rejected comic profile of " + submission.getTitle() + " (all chapters rejected)");
            }
        }

        ComicEntity comic = resolveComic(submission);
        if (comic != null) {
            chapterCrudPlugin.evictChaptersCache(comic.getId());
        }

        SubmissionDTO responseDto = crudPlugin.getPlugin().toDto(savedSubmission);
        responseDto.setComicAutoRejected(comicAutoRejected);

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(responseDto)
                .build());
    }

    private void notifySubmissionOwner(SubmissionEntity submission, boolean approved, String rejectionReason) {
        boolean translatorSubmission = "translator".equalsIgnoreCase(submission.getQueueType());
        UUID recipientId = submission.getAuthorId();
        if (recipientId == null && translatorSubmission) {
            ProjectTeamEntity team = findSubmissionTeam(submission);
            recipientId = team == null ? null : team.getLeaderId();
        }
        String subject = submission.getChapter() == null || submission.getChapter().isBlank()
                ? submission.getTitle()
                : submission.getTitle() + " - " + submission.getChapter();
        String message = approved
                ? subject + " was approved and is ready for the next workflow step."
                : subject + " needs changes. Reason: " + rejectionReason;
        notificationService.notifyUser(
                recipientId,
                approved ? "Submission approved" : "Submission needs changes",
                message,
                approved ? "UPDATE" : "WARNING",
                translatorSubmission
                        ? com.sep.comiverse.entity.enums.NotificationPreferenceKey.TEAM_UPDATES
                        : com.sep.comiverse.entity.enums.NotificationPreferenceKey.SUBMISSION_STATUS
        );
    }
    
    private void notifyTeamOfNewChapters(ComicEntity comic, String title) {
        if (comic == null) return;
        List<com.sep.comiverse.entity.ProjectTeamEntity> teams = projectTeamRepository.findAllByComicNameIgnoreCase(comic.getTitle());
        for (com.sep.comiverse.entity.ProjectTeamEntity team : teams) {
            if (team.getLeaderId() != null) {
                notificationService.notifyUser(
                        team.getLeaderId(),
                        "New chapter in Backlog",
                        "A new chapter '" + title + "' has been approved and added to the backlog of " + team.getTitle(),
                        "UPDATE",
                        com.sep.comiverse.entity.enums.NotificationPreferenceKey.TEAM_UPDATES
                );
            }
        }
    }

    private ProjectTeamEntity findSubmissionTeam(SubmissionEntity submission) {
        if (submission == null) {
            return null;
        }
        String teamName = submission.getSubmittedBy();
        String comicTitle = submission.getTitle();
        if (teamName != null) {
            var teamOpt = projectTeamRepository.findByTitleIgnoreCase(teamName);
            if (teamOpt.isPresent()) {
                return teamOpt.get();
            }
        }
        if (comicTitle != null) {
            return projectTeamRepository.findByComicNameIgnoreCase(comicTitle).orElse(null);
        }
        return null;
    }
}
