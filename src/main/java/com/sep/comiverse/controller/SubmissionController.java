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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/submissions")
@PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
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

    private static final Pattern CHAPTER_NUMBER_PATTERN =
            Pattern.compile("(?i)chapter\\s+([0-9]+(?:[,.][0-9]+)?)");
    @Autowired
    private com.sep.comiverse.repository.ITeamTaskRepository teamTaskRepository;

    @Autowired
    public SubmissionController(SubmissionCrudPlugin crud) {
        super(crud, SubmissionEntity.class);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<SubmissionDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<SubmissionDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @PutMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubmissionEntity submission = submissionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        boolean alreadyApproved = "approved".equalsIgnoreCase(submission.getStatus());
        submission.setStatus("approved");
        if (principal != null) {
            submission.setModeratorId(principal.getId());
        }
        SubmissionEntity savedSubmission = submissionRepository.save(submission);

        if (!alreadyApproved) {
            String targetDesc = (submission.getChapter() != null && !submission.getChapter().isBlank())
                    ? "chapter " + submission.getChapter()
                    : "Comic profile";
            auditLogService.log("REVIEW_QUEUE", "Approved " + targetDesc + " of " + submission.getTitle());
            if ("author".equalsIgnoreCase(submission.getQueueType())) {
                handleAuthorApproval(submission);
            } else if ("translator".equalsIgnoreCase(submission.getQueueType())) {
                handleTranslatorApproval(submission);
            }
        }

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }

    private void handleAuthorApproval(SubmissionEntity submission) {
        ComicEntity comic = resolveComic(submission);
        if (comic != null) {
            if (submission.getChapterId() == null) {
                comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                comic.setLastChapterUpdatedAt(Instant.now());
            } else {
                chapterRepository.findById(submission.getChapterId()).ifPresent(chapter -> {
                    chapter.setModerationStatus(ChapterStatus.PUBLISHED);
                    chapterRepository.save(chapter);
                });
                updateLatestChapterIfAuthorChapterSubmission(comic, submission);
                // Automatically push task to backlog disabled - now handled by Chapter Backlog
            }
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
            ChapterEntity savedChapter = chapterRepository.save(chapter);

            ComicEntity comic = savedChapter.getComic();
            if (comic != null) {
                refreshComicMetadataAfterPublishedChapter(comic, savedChapter);
                comicRepository.save(comic);
            }

            return;
        }

        /*
         * Case 2: Author submit comic profile/review.
         * Khi moderator approve comic submission:
         * - lấy đúng ComicEntity theo submission.comicId
         * - set moderationStatus = PUBLISHED
         */
        if (submission.getComicId() != null) {
            ComicEntity comic = comicRepository.findById(submission.getComicId())
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Comic with id " + submission.getComicId() + " not found"
                    ));

            comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
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
            comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
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

    private void handleTranslatorApproval(SubmissionEntity submission) {
        String teamName = submission.getSubmittedBy();
        String comicTitle = submission.getTitle();

        ProjectTeamEntity team = projectTeamRepository.findAll().stream()
                .filter(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(teamName))
                .findFirst()
                .orElse(null);

        if (team == null) {
            team = projectTeamRepository.findAll().stream()
                    .filter(t -> t.getComicName() != null && t.getComicName().equalsIgnoreCase(comicTitle))
                    .findFirst()
                    .orElse(null);
        }

            ComicEntity comic = comicRepository.findAllByTitle(comicTitle).stream().findFirst()
                    .or(() -> comicRepository.findAllByTitleIgnoreCase(comicTitle).stream().findFirst())
                    .orElse(null);

        if (team != null) {
            team.setChaptersCount(team.getChaptersCount() == null ? 1 : team.getChaptersCount() + 1);

            if (comic != null) {
                String chapterNumber = extractChapterNumber(submission.getChapter());
                if (chapterNumber == null) {
                    chapterNumber = "1";
                }
                if (!chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comic.getId(), chapterNumber)) {
                    ChapterEntity chapter = ChapterEntity.builder()
                            .chapterNumber(chapterNumber)
                            .title(submission.getChapter() != null ? submission.getChapter() : "Chapter " + chapterNumber)
                            .images(List.of("https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"))
                            .moderationStatus(ChapterStatus.PUBLISHED)
                            .comic(comic)
                            .projectTeam(team)
                            .build();
                    chapterRepository.save(chapter);
                }
                comic.setLatestChapterNumber(chapterNumber);
                comic.setLastChapterUpdatedAt(Instant.now());
                long chapterCount = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(comic.getId(), ChapterStatus.PUBLISHED);
                comic.setChapterCount(chapterCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) chapterCount);
                comicRepository.save(comic);
            }
            projectTeamRepository.save(team);
        } else if (comic != null) {
            String chapterNumber = extractChapterNumber(submission.getChapter());
            if (chapterNumber != null) {
                comic.setLatestChapterNumber(chapterNumber);
            }
            comic.setLastChapterUpdatedAt(Instant.now());
            comicRepository.save(comic);
        }
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

    private void handleSubmissionRejected(SubmissionEntity submission) {
        if (submission == null || !"author".equalsIgnoreCase(submission.getQueueType())) {
            return;
        }
        if (submission.getChapterId() != null) {
            chapterRepository.findById(submission.getChapterId()).ifPresent(chapter -> {
                chapter.setModerationStatus(ChapterStatus.REJECTED);
                chapterRepository.save(chapter);
            });
            return;
        }
        if (submission.getComicId() != null) {
            comicRepository.findById(submission.getComicId()).ifPresent(comic -> {
                comic.setModerationStatus(ComicModerationStatus.REJECTED);
                comicRepository.save(comic);
            });
        }
    }

    private String buildSafeSlug(String title) {
        String baseSlug = title == null ? "comic" : title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (baseSlug.isBlank()) {
            baseSlug = "comic";
        }
        String slug = baseSlug;
        int suffix = 2;
        while (comicRepository.existsBySlugAndDeletedFalse(slug)) {
            slug = baseSlug + "-" + suffix++;
        }
        return slug;
    }

    @PutMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<BaseResponse<SubmissionDTO>> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubmissionEntity submission = submissionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Submission with id " + id + " not found"));

        String reason = body != null ? body.getOrDefault("reason", "No reason provided.") : "No reason provided.";
        submission.setStatus("rejected");
        submission.setRejectionReason(reason);
        if (principal != null) {
            submission.setModeratorId(principal.getId());
        }

        SubmissionEntity savedSubmission = submissionRepository.save(submission);
        String targetDesc = "author".equalsIgnoreCase(submission.getQueueType()) ? "Comic profile" : "chapter " + submission.getChapter();
        auditLogService.log("REVIEW_QUEUE", "Rejected " + targetDesc + " of " + submission.getTitle() + " (Reason: " + reason + ")");
        handleSubmissionRejected(submission);

        return ResponseEntity.ok(BaseResponse.<SubmissionDTO>builder()
                .success(true)
                .data(crudPlugin.getPlugin().toDto(savedSubmission))
                .build());
    }
}
