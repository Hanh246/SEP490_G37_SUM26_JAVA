package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.CreateReportRequest;
import com.sep.comiverse.dto.request.ProcessReportRequest;
import com.sep.comiverse.dto.request.ReportFilterDTO;
import com.sep.comiverse.dto.response.ReportResponse;
import com.sep.comiverse.entity.*;
import com.sep.comiverse.entity.enums.*;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.util.LanguageCodes;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final IReportRepository reportRepository;
    private final IReportCategoryRepository reportCategoryRepository;
    private final IUserRepository userRepository;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final ITeamMessageRepository teamMessageRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final ComicCrudPlugin comicCrudPlugin;
    private final ChapterCrudPlugin chapterCrudPlugin;
    private final NotificationService notificationService;



    @Transactional
    public ReportResponse createReport(UUID reporterId, CreateReportRequest request) {
        if (reporterId == null) {
            throw new CustomException(401, "User is not authenticated", HttpStatus.UNAUTHORIZED);
        }

        ReportCategoryEntity category = reportCategoryRepository.findByIdAndDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new CustomException(400, "Report category does not exist or is inactive", HttpStatus.BAD_REQUEST));

        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new CustomException(400, "Report category does not exist or is inactive", HttpStatus.BAD_REQUEST);
        }

        if (!category.supportsTargetType(request.getTargetType())) {
            throw new CustomException(400, "Report category '" + category.getName() + "' does not apply to " + request.getTargetType(), HttpStatus.BAD_REQUEST);
        }

        ResolvedTarget resolved = resolveReportTarget(request.getTargetType(), request.getTargetId(), request.getLanguageCode());

        boolean hasActiveReport = reportRepository.existsByReporter_IdAndTargetTypeAndTargetIdAndStatusInAndDeletedFalse(
                reporterId,
                request.getTargetType(),
                resolved.targetId(),
                List.of(ReportStatus.PENDING, ReportStatus.IN_PROGRESS)
        );

        if (hasActiveReport) {
            throw new CustomException(
                    400,
                    "You already have a pending report for this item. Please wait for it to be processed.",
                    HttpStatus.BAD_REQUEST
            );
        }

        UserEntity reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(404, "User account not found", HttpStatus.NOT_FOUND));

        // 4. Save new Report with status = PENDING
        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(resolved.targetId())
                .category(category)
                .descriptionText(request.getDescriptionText() != null ? request.getDescriptionText().trim() : null)
                .status(ReportStatus.PENDING)
                .build();

        ReportEntity saved = reportRepository.save(report);
        log.info("New report created: id={}, reporterId={}, targetType={}, targetId={}, categoryId={}",
                saved.getId(), reporterId, request.getTargetType(), resolved.targetId(), category.getId());

        // Notify Moderators or Project Leaders according to category.assignedRole
        try {
            String assignedRoleName = category.getAssignedRole() != null ? category.getAssignedRole().name() : "MODERATOR";
            notificationService.notifyRoles(
                    List.of(assignedRoleName, "ADMIN"),
                    "New Issue Report",
                    "A new report was submitted for " + resolved.title() + " (" + category.getName() + ")",
                    "REPORT_SUBMITTED",
                    NotificationPreferenceKey.REVIEW_QUEUE
            );
        } catch (Exception e) {
            log.warn("Failed to send workflow notification for report creation: {}", e.getMessage());
        }

        return toResponse(saved, resolved.title());
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getReports(UserEntity currentUser, ReportFilterDTO filterDTO) {
        ReportFilterDTO safeFilter = filterDTO != null ? filterDTO : new ReportFilterDTO();

        boolean isProjectLeader = currentUser != null && currentUser.getRole() != null
                && "PROJECT_LEADER".equalsIgnoreCase(currentUser.getRole().getRoleName());
        boolean isTranslator = currentUser != null && currentUser.getRole() != null
                && "TRANSLATOR".equalsIgnoreCase(currentUser.getRole().getRoleName());
        boolean isModerator = currentUser != null && currentUser.getRole() != null
                && "MODERATOR".equalsIgnoreCase(currentUser.getRole().getRoleName());

        boolean isTeamMemberOrLeader = isProjectLeader || isTranslator;

        // Determine effective assigned role filter based on user role
        ReportAssignedRole effectiveAssignedRole = safeFilter.getAssignedRole();
        if (isModerator && effectiveAssignedRole == null) {
            effectiveAssignedRole = ReportAssignedRole.MODERATOR;
        } else if (isTeamMemberOrLeader && effectiveAssignedRole == null) {
            effectiveAssignedRole = ReportAssignedRole.PROJECT_LEADER;
        }

        ReportAssignedRole finalAssignedRole = effectiveAssignedRole;

        List<UUID> teamTranslationIds = (isTeamMemberOrLeader && currentUser.getId() != null)
                ? chapterTranslationRepository.findTranslationIdsByUserId(currentUser.getId())
                : null;

        Specification<ReportEntity> spec = (root, query, cb) -> {
            var predicate = cb.and(
                    cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted")))
            );

            if (safeFilter.getStatus() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), safeFilter.getStatus()));
            }

            if (safeFilter.getTargetType() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("targetType"), safeFilter.getTargetType()));
            }

            if (safeFilter.getCategoryId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("category").get("id"), safeFilter.getCategoryId()));
            }

            if (safeFilter.getReporterId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("reporter").get("id"), safeFilter.getReporterId()));
            }

            if (safeFilter.getHandlerId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("handler").get("id"), safeFilter.getHandlerId()));
            }

            if (finalAssignedRole != null) {
                Join<ReportEntity, ReportCategoryEntity> categoryJoin = root.join("category", JoinType.INNER);
                predicate = cb.and(predicate, cb.equal(categoryJoin.get("assignedRole"), finalAssignedRole));
            }

            if (safeFilter.getStartDate() != null && !safeFilter.getStartDate().isBlank()) {
                try {
                    java.time.LocalDate start = java.time.LocalDate.parse(safeFilter.getStartDate());
                    predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
                } catch (Exception e) {
                    // Ignore invalid date
                }
            }

            if (safeFilter.getEndDate() != null && !safeFilter.getEndDate().isBlank()) {
                try {
                    java.time.LocalDate end = java.time.LocalDate.parse(safeFilter.getEndDate());
                    // End of the day
                    predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdAt"), end.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusMillis(1)));
                } catch (Exception e) {
                    // Ignore invalid date
                }
            }

            if (isTeamMemberOrLeader) {
                var targetTypePredicate = cb.equal(root.get("targetType"), ReportTargetType.CHAPTER_TRANSLATIONS);
                if (teamTranslationIds == null || teamTranslationIds.isEmpty()) {
                    predicate = cb.and(predicate, cb.disjunction());
                } else {
                    predicate = cb.and(predicate, targetTypePredicate, root.get("targetId").in(teamTranslationIds));
                }
            }

            return predicate;
        };

        String sortBy = (safeFilter.getSortBy() != null && !safeFilter.getSortBy().isBlank())
                ? safeFilter.getSortBy() : "createdAt";
        Sort.Direction direction = "ASC".equalsIgnoreCase(safeFilter.getSortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                Math.max(0, safeFilter.getPage()),
                Math.max(1, safeFilter.getSize()),
                Sort.by(direction, sortBy)
        );

        Page<ReportEntity> page = reportRepository.findAll(spec, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(UUID reporterId, Pageable pageable) {
        return reportRepository.findAllByReporter_IdAndDeletedFalse(reporterId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReportById(UUID reportId, UserEntity currentUser) {
        ReportEntity report = reportRepository.findByIdWithDetails(reportId)
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> new CustomException(404, "Report not found", HttpStatus.NOT_FOUND));

        if (currentUser != null && currentUser.getRole() != null) {
            String roleName = currentUser.getRole().getRoleName();
            boolean isStaff = "ADMIN".equalsIgnoreCase(roleName)
                    || "MODERATOR".equalsIgnoreCase(roleName)
                    || "PROJECT_LEADER".equalsIgnoreCase(roleName)
                    || "TRANSLATOR".equalsIgnoreCase(roleName);

            boolean isReporter = report.getReporter() != null && report.getReporter().getId().equals(currentUser.getId());

            if (!isStaff && !isReporter) {
                throw new CustomException(403, "You do not have permission to view this report", HttpStatus.FORBIDDEN);
            }

            if (("PROJECT_LEADER".equalsIgnoreCase(roleName) || "TRANSLATOR".equalsIgnoreCase(roleName)) && !isReporter) {
                if (report.getTargetType() == ReportTargetType.CHAPTER_TRANSLATIONS) {
                    boolean isTeamMember = chapterTranslationRepository.isUserMemberOrLeaderOfTranslation(report.getTargetId(), currentUser.getId());
                    if (!isTeamMember) {
                        throw new CustomException(403, "You do not have permission to view reports for this translation", HttpStatus.FORBIDDEN);
                    }
                } else {
                    throw new CustomException(403, "Project team members can only view translation reports", HttpStatus.FORBIDDEN);
                }
            }
        }

        return toResponse(report);
    }


    @Transactional
    public ReportResponse processReport(UUID reportId, UserEntity handler, ProcessReportRequest request) {
        if (request == null || request.getAction() == null) {
            throw new CustomException(400, "Processing action is required (ACCEPT or REJECT)", HttpStatus.BAD_REQUEST);
        }

        ReportEntity report = reportRepository.findByIdWithDetails(reportId)
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> new CustomException(404, "Report not found", HttpStatus.NOT_FOUND));

        if (isTerminalReportStatus(report.getStatus())) {
            throw new CustomException(400, "This report has already been resolved (" + report.getStatus() + ")", HttpStatus.BAD_REQUEST);
        }

        // Validate handler permissions for role-routing
        if (handler != null && handler.getRole() != null) {
            String roleName = handler.getRole().getRoleName();
            if (!"ADMIN".equalsIgnoreCase(roleName)) {
                ReportAssignedRole assignedRole = report.getCategory().getAssignedRole();
                if (assignedRole == ReportAssignedRole.MODERATOR && !"MODERATOR".equalsIgnoreCase(roleName)) {
                    throw new CustomException(403, "Only Moderators or Administrators can process this report", HttpStatus.FORBIDDEN);
                }
                if (assignedRole == ReportAssignedRole.PROJECT_LEADER) {
                    if (!"PROJECT_LEADER".equalsIgnoreCase(roleName)) {
                        throw new CustomException(403, "Only Project Leaders or Administrators can process this report", HttpStatus.FORBIDDEN);
                    }
                    if (report.getTargetType() == ReportTargetType.CHAPTER_TRANSLATIONS) {
                        boolean isLeader = chapterTranslationRepository.isUserLeaderOfTranslation(report.getTargetId(), handler.getId());
                        if (!isLeader) {
                            throw new CustomException(403, "Only the team leader of this translation can process this report", HttpStatus.FORBIDDEN);
                        }
                    }
                }
            }
        }

        String targetDisplayName = getTargetDisplayName(report.getTargetType(), report.getTargetId());
        String resolutionNote = request.getResolutionNote() != null ? request.getResolutionNote().trim() : null;

        if (request.getAction() == ReportAction.ACCEPT) {
            if (report.getTargetType() == ReportTargetType.CHAPTER_TRANSLATIONS
                    && (resolutionNote == null || resolutionNote.isEmpty())) {
                throw new CustomException(400, "Resolution note is required when accepting a translation report", HttpStatus.BAD_REQUEST);
            }

            report.setStatus(ReportStatus.ACCEPTED);
            report.setHandler(handler);
            report.setResolvedAt(Instant.now());
            if (resolutionNote != null && !resolutionNote.isEmpty()) {
                report.setResolutionNote(resolutionNote);
            }

            // Revoke / unpublish reported content (Comic / Chapter / Translation)
            revokeReportedTarget(report, resolutionNote);

            ReportEntity saved = reportRepository.save(report);
            log.info("Report accepted and content revoked: id={}, targetType={}, targetId={}, handlerId={}",
                    saved.getId(), report.getTargetType(), report.getTargetId(), handler != null ? handler.getId() : null);

            // Trigger Notification to Reporter
            try {
                String notifMsg = "Your report for " + targetDisplayName + " has been accepted and processed. Thank you for your contribution!";
                if (resolutionNote != null && !resolutionNote.isEmpty()) {
                    notifMsg += " Note: " + resolutionNote;
                }
                notificationService.notifyUser(
                        saved.getReporter().getId(),
                        "Report Accepted",
                        notifMsg,
                        "REPORT_ACCEPTED",
                        NotificationPreferenceKey.SYSTEM_BROADCASTS
                );
            } catch (Exception e) {
                log.warn("Failed to notify user on report accept: {}", e.getMessage());
            }

            return toResponse(saved, targetDisplayName);

        } else if (request.getAction() == ReportAction.REJECT) {
            if (resolutionNote == null || resolutionNote.isEmpty()) {
                throw new CustomException(400, "Resolution note is required when rejecting a report.", HttpStatus.BAD_REQUEST);
            }

            report.setStatus(ReportStatus.REJECTED);
            report.setHandler(handler);
            report.setResolvedAt(Instant.now());
            report.setResolutionNote(resolutionNote);

            ReportEntity saved = reportRepository.save(report);
            log.info("Report rejected: id={}, handlerId={}, reason={}", saved.getId(), handler != null ? handler.getId() : null, resolutionNote);

            // Trigger Notification to Reporter
            try {
                String notifMsg = "Your report for " + targetDisplayName + " was rejected. Reason: " + resolutionNote;
                notificationService.notifyUser(
                        saved.getReporter().getId(),
                        "Report Rejected",
                        notifMsg,
                        "REPORT_REJECTED",
                        NotificationPreferenceKey.SYSTEM_BROADCASTS
                );
            } catch (Exception e) {
                log.warn("Failed to notify user on report reject: {}", e.getMessage());
            }

            return toResponse(saved, targetDisplayName);
        } else {
            throw new CustomException(400, "Invalid report action: " + request.getAction(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public int markAcceptedLeaderTranslationReportsDone(UUID translationId) {
        if (translationId == null) {
            return 0;
        }
        List<ReportEntity> reports = reportRepository
                .findByTargetTypeAndTargetIdAndStatusAndCategory_AssignedRoleAndDeletedFalse(
                        ReportTargetType.CHAPTER_TRANSLATIONS,
                        translationId,
                        ReportStatus.ACCEPTED,
                        ReportAssignedRole.PROJECT_LEADER
                );
        if (reports.isEmpty()) {
            return 0;
        }
        for (ReportEntity report : reports) {
            report.setStatus(ReportStatus.DONE);
        }
        reportRepository.saveAll(reports);
        log.info("Marked {} leader translation report(s) DONE after revision publish: translationId={}",
                reports.size(), translationId);
        return reports.size();
    }

    private boolean isTerminalReportStatus(ReportStatus status) {
        return status == ReportStatus.ACCEPTED
                || status == ReportStatus.REJECTED
                || status == ReportStatus.DONE;
    }

    private void revokeReportedTarget(ReportEntity report, String resolutionNote) {
        if (report == null || report.getTargetType() == null || report.getTargetId() == null) {
            return;
        }

        String reason = (resolutionNote != null && !resolutionNote.isBlank())
                ? resolutionNote
                : "Report accepted: content violates platform guidelines";

        switch (report.getTargetType()) {
            case COMIC -> {
                comicRepository.findById(report.getTargetId())
                        .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                        .ifPresent(comic -> {
                            comic.setModerationStatus(ComicModerationStatus.UNPUBLISHED);
                            comic.setRejectionReason(reason);
                            comicRepository.save(comic);

                            try {
                                if (comicCrudPlugin != null) {
                                    comicCrudPlugin.evictComicCache(comic.getId());
                                }
                            } catch (Exception ex) {
                                log.warn("Failed to evict comic cache: {}", ex.getMessage());
                            }

                            if (comic.getAuthorId() != null) {
                                try {
                                    notificationService.notifyUser(
                                            comic.getAuthorId(),
                                            "Comic Unpublished",
                                            "Your comic \"" + comic.getTitle() + "\" has been unpublished due to an accepted issue report. Reason: " + reason,
                                            "COMIC_UNPUBLISHED",
                                            NotificationPreferenceKey.REVIEW_QUEUE
                                    );
                                } catch (Exception ex) {
                                    log.warn("Failed to notify author for comic unpublish: {}", ex.getMessage());
                                }
                            }
                            log.info("Revoked/Unpublished comic id={} due to accepted report id={}", comic.getId(), report.getId());
                        });
            }
            case CHAPTER -> {
                chapterRepository.findById(report.getTargetId())
                        .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                        .ifPresent(chapter -> {
                            chapter.setModerationStatus(ChapterStatus.UNPUBLISHED);
                            chapter.setRejectionReason(reason);
                            chapterRepository.save(chapter);

                            UUID comicId = (chapter.getComic() != null) ? chapter.getComic().getId() : null;
                            try {
                                if (chapterCrudPlugin != null) {
                                    chapterCrudPlugin.evictChapterDetailCache(chapter.getId());
                                    if (comicId != null) {
                                        chapterCrudPlugin.evictChaptersCache(comicId);
                                    }
                                }
                                if (comicCrudPlugin != null && comicId != null) {
                                    comicCrudPlugin.evictComicCache(comicId);
                                }
                            } catch (Exception ex) {
                                log.warn("Failed to evict chapter cache: {}", ex.getMessage());
                            }

                            if (chapter.getComic() != null && chapter.getComic().getAuthorId() != null) {
                                try {
                                    String comicTitle = chapter.getComic().getTitle() != null ? chapter.getComic().getTitle() : "Comic";
                                    notificationService.notifyUser(
                                            chapter.getComic().getAuthorId(),
                                            "Chapter Unpublished",
                                            "Chapter " + chapter.getChapterNumber() + " of \"" + comicTitle + "\" has been unpublished due to an accepted issue report. Reason: " + reason,
                                            "CHAPTER_UNPUBLISHED",
                                            NotificationPreferenceKey.REVIEW_QUEUE
                                    );
                                } catch (Exception ex) {
                                    log.warn("Failed to notify author for chapter unpublish: {}", ex.getMessage());
                                }
                            }
                            log.info("Revoked/Unpublished chapter id={} due to accepted report id={}", chapter.getId(), report.getId());
                        });
            }
            case CHAPTER_TRANSLATIONS -> {
                chapterTranslationRepository.findById(report.getTargetId())
                        .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                        .ifPresent(translation -> {
                            // Keep the live translation published. A later revision task updates it in place.

                            UUID chapterId = translation.getChapter() != null ? translation.getChapter().getId() : null;
                            if (chapterId != null) {
                                List<TeamTaskEntity> relatedTasks = teamTaskRepository.findByChapter_Id(chapterId).stream()
                                        .filter(task -> translation.getProjectTeamId() == null
                                                || translation.getProjectTeamId().equals(task.getProjectTeamId()))
                                        .toList();
                                for (TeamTaskEntity relatedTask : relatedTasks) {
                                    relatedTask.setRejectionReason(reason);
                                }
                                if (!relatedTasks.isEmpty()) {
                                    teamTaskRepository.saveAll(relatedTasks);
                                }
                            }

                            if (translation.getProjectTeamId() != null) {
                                projectTeamRepository.findById(translation.getProjectTeamId())
                                        .filter(pt -> !Boolean.TRUE.equals(pt.getDeleted()))
                                        .ifPresent(team -> {
                                            Set<UUID> memberUserIds = new HashSet<>();
                                            if (team.getLeaderId() != null) memberUserIds.add(team.getLeaderId());
                                            if (team.getMembers() != null) {
                                                for (ProjectTeamMemberEntity mem : team.getMembers()) {
                                                    if (mem.getUser() != null && mem.getUser().getId() != null) {
                                                        memberUserIds.add(mem.getUser().getId());
                                                    }
                                                }
                                            }

                                            String chapterNum = translation.getChapter() != null ? translation.getChapter().getChapterNumber() : "";
                                            String comicTitle = (translation.getChapter() != null && translation.getChapter().getComic() != null)
                                                    ? translation.getChapter().getComic().getTitle()
                                                    : "Comic";
                                            String reportReason = report.getDescriptionText() != null ? report.getDescriptionText() : "Translation issue reported";
                                            String notifMsg = "Translation (" + translation.getLanguageCode() + ") for Chapter " + chapterNum + " of \"" + comicTitle + "\" received an accepted reader report: \"" + reportReason + "\". Readers can still read the current translation. Create a revision task to fix it. Resolution note: \"" + reason + "\"";

                                            for (UUID recipientId : memberUserIds) {
                                                try {
                                                    notificationService.notifyUser(
                                                            recipientId,
                                                            "⚠️ Translation Report Accepted",
                                                            notifMsg,
                                                            "TRANSLATION_REVISION_REQUESTED",
                                                            NotificationPreferenceKey.REVIEW_QUEUE
                                                    );
                                                } catch (Exception ex) {
                                                    log.warn("Failed to notify user {} for translation revision request: {}", recipientId, ex.getMessage());
                                                }
                                            }

                                            // Send WebSocket group chat warning message to team
                                            try {
                                                TeamMessageEntity warningMsg = TeamMessageEntity.builder()
                                                        .projectTeamId(team.getId())
                                                        .sender("SYSTEM")
                                                        .avatar("🚨")
                                                        .text("🚨 [TRANSLATION REPORT] Chapter " + chapterNum + " needs a revision task. The current translation stays available to readers until the new task is published. Reader report reason: \"" + reportReason + "\". Resolution plan: \"" + reason + "\"")
                                                        .time(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                                                        .build();
                                                TeamMessageEntity savedMsg = teamMessageRepository.save(warningMsg);
                                                messagingTemplate.convertAndSend("/topic/team-workspace/" + team.getId(), savedMsg);
                                            } catch (Exception ex) {
                                                log.warn("Failed to post system warning message to group chat: {}", ex.getMessage());
                                            }

                                        });
                            }

                            log.info("Translation report accepted without unpublishing: translationId={}, reportId={}. Team can create a revision task.",
                                    translation.getId(), report.getId());
                        });
            }
            default -> log.warn("No revocation handler for report target type: {}", report.getTargetType());
        }
    }


    private record ResolvedTarget(UUID targetId, String title) {}

    private ResolvedTarget resolveReportTarget(ReportTargetType targetType, UUID targetId, String languageCode) {
        if (targetType == ReportTargetType.CHAPTER_TRANSLATIONS) {
            ChapterTranslationEntity translation = resolveTranslationTarget(targetId, languageCode);
            return new ResolvedTarget(translation.getId(), translationTitle(translation));
        }
        return new ResolvedTarget(targetId, validateAndGetTargetTitle(targetType, targetId));
    }

    private ChapterTranslationEntity resolveTranslationTarget(UUID targetId, String languageCode) {
        if (targetId == null) {
            throw new CustomException(400, "Target type and target ID must not be null", HttpStatus.BAD_REQUEST);
        }

        Optional<ChapterTranslationEntity> byId = chapterTranslationRepository.findById(targetId)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()));
        if (byId.isPresent()) {
            return byId.get();
        }

        List<ChapterTranslationEntity> byChapter = chapterTranslationRepository.findByChapter_Id(targetId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .toList();
        if (byChapter.isEmpty()) {
            throw new CustomException(404, "Reported chapter translation not found in the system", HttpStatus.NOT_FOUND);
        }

        List<ChapterTranslationEntity> candidates = byChapter;
        if (languageCode != null && !languageCode.isBlank()) {
            String wanted = LanguageCodes.normalize(languageCode);
            candidates = byChapter.stream()
                    .filter(t -> wanted.equals(LanguageCodes.normalize(t.getLanguageCode())))
                    .toList();
            if (candidates.isEmpty()) {
                throw new CustomException(404, "Reported chapter translation not found in the system", HttpStatus.NOT_FOUND);
            }
        }

        Optional<ChapterTranslationEntity> published = candidates.stream()
                .filter(t -> t.getStatus() == null || t.getStatus() == ChapterTranslationStatus.PUBLISHED)
                .findFirst();
        if (published.isPresent()) {
            return published.get();
        }
        if (candidates.size() == 1 || (languageCode != null && !languageCode.isBlank())) {
            return candidates.get(0);
        }
        throw new CustomException(400, "Multiple translations exist for this chapter. Report a specific language.", HttpStatus.BAD_REQUEST);
    }

    private String translationTitle(ChapterTranslationEntity translation) {
        String comicTitle = "Comic";
        String chapterNumber = "";
        if (translation.getChapter() != null) {
            chapterNumber = translation.getChapter().getChapterNumber();
            if (translation.getChapter().getComic() != null && translation.getChapter().getComic().getTitle() != null) {
                comicTitle = translation.getChapter().getComic().getTitle();
            }
        }
        return comicTitle + " - Chapter " + chapterNumber + " (Translation: " + translation.getLanguageCode() + ")";
    }

    private String validateAndGetTargetTitle(ReportTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            throw new CustomException(400, "Target type and target ID must not be null", HttpStatus.BAD_REQUEST);
        }

        switch (targetType) {
            case COMIC -> {
                ComicEntity comic = comicRepository.findById(targetId)
                        .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                        .orElseThrow(() -> new CustomException(404, "Reported comic not found in the system", HttpStatus.NOT_FOUND));
                return "Comic: " + comic.getTitle();
            }
            case CHAPTER -> {
                ChapterEntity chapter = chapterRepository.findById(targetId)
                        .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                        .orElseThrow(() -> new CustomException(404, "Reported chapter not found in the system", HttpStatus.NOT_FOUND));
                String comicTitle = (chapter.getComic() != null && chapter.getComic().getTitle() != null)
                        ? chapter.getComic().getTitle() : "Comic";
                return comicTitle + " - Chapter " + chapter.getChapterNumber() + (chapter.getTitle() != null && !chapter.getTitle().isBlank() ? ": " + chapter.getTitle() : "");
            }
            case CHAPTER_TRANSLATIONS -> {
                ChapterTranslationEntity translation = resolveTranslationTarget(targetId, null);
                return translationTitle(translation);
            }
            default -> throw new CustomException(400, "Unsupported report target type: " + targetType, HttpStatus.BAD_REQUEST);
        }
    }

    private String getTargetDisplayName(ReportTargetType targetType, UUID targetId) {
        try {
            return validateAndGetTargetTitle(targetType, targetId);
        } catch (Exception e) {
            return targetType + " (ID: " + targetId + ")";
        }
    }

    public ReportResponse toResponse(ReportEntity entity) {
        String targetTitle = getTargetDisplayName(entity.getTargetType(), entity.getTargetId());
        return toResponse(entity, targetTitle);
    }

    public ReportResponse toResponse(ReportEntity entity, String targetTitle) {
        if (entity == null) return null;

        UserEntity reporter = entity.getReporter();
        UserEntity handler = entity.getHandler();
        ReportCategoryEntity category = entity.getCategory();

        return ReportResponse.builder()
                .id(entity.getId())
                .reporterId(reporter != null ? reporter.getId() : null)
                .reporterName(reporter != null ? reporter.getFullName() : null)
                .reporterEmail(reporter != null ? reporter.getEmail() : null)
                .reporterAvatarUrl(reporter != null ? reporter.getAvatarUrl() : null)
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetTitle(targetTitle)
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryAssignedRole(category != null ? category.getAssignedRole() : null)
                .descriptionText(entity.getDescriptionText())
                .status(entity.getStatus())
                .handlerId(handler != null ? handler.getId() : null)
                .handlerName(handler != null ? handler.getFullName() : null)
                .handlerEmail(handler != null ? handler.getEmail() : null)
                .resolutionNote(entity.getResolutionNote())
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
