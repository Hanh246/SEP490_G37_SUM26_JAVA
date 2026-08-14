package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IReviewCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.util.LanguageCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamTaskReviewService {

    private final ITeamTaskRepository taskRepository;
    private final IPageTranslationRepository pageTranslationRepository;
    private final IProjectTeamRepository projectTeamRepository;
    private final IChapterRepository chapterRepository;
    private final IComicRepository comicRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;
    private final IReviewCommentRepository reviewCommentRepository;
    private final ChapterCrudPlugin chapterCrudPlugin;
    private final TranslatorPaymentService translatorPaymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TeamTaskEntity returnToInProgress(TeamTaskEntity task) {
        task.setStatus("in_progress");
        task.setCompletedAt(null);
        return taskRepository.save(task);
    }

    @Transactional
    public TeamTaskEntity approveAndPublish(TeamTaskEntity task, UUID reviewerId) {
        long openReviews = reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(task.getId());
        if (openReviews > 0) {
            throw new CustomException(
                    409,
                    "This chapter has review comments. Use Request Changes instead of Accept.",
                    HttpStatus.CONFLICT
            );
        }
        List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId());
        if (pages.isEmpty()) {
            throw new CustomException(409, "The task has no pages to publish", HttpStatus.CONFLICT);
        }
        task.setStatus("completed");
        task.setCompletedAt(Instant.now());
        task.setRejectionReason(null);

        log.info("[approveAndPublish] found {} PageTranslationEntity rows for taskId={}", pages.size(), task.getId());
        for (PageTranslationEntity page : pages) {
            page.setReviewBaselineBubbles(page.getBubbles());
        }
        pageTranslationRepository.saveAll(pages);

        publishChapterFromTask(task, pages, reviewerId);
        TeamTaskEntity saved = taskRepository.save(task);
        translatorPaymentService.settleApprovedTask(saved);
        return saved;
    }

    private void publishChapterFromTask(TeamTaskEntity task, List<PageTranslationEntity> pages, UUID modId) {
        ChapterEntity chapter = task.getChapter();
        if (chapter == null) {
            log.warn("[publishChapterFromTask] task {} has no chapter linked, skipping publish", task.getId());
            return;
        }

        chapter.setModerationStatus(ChapterStatus.PUBLISHED);
        chapter.setApprovedById(modId);
        chapter.setApprovedAt(Instant.now());
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

        String languageCode = LanguageCodes.normalize(team != null ? team.getTargetLang() : null);
        String pagesBubblesJson = buildPagesBubblesJson(pages);
        UUID teamId = task.getProjectTeamId();

        ChapterTranslationEntity translation = chapterTranslationRepository.findByChapter_Id(savedChapter.getId())
                .stream()
                .filter(existing -> languageCode.equals(LanguageCodes.normalize(existing.getLanguageCode())))
                .findFirst()
                .orElseGet(ChapterTranslationEntity::new);

        translation.setChapter(savedChapter);
        translation.setLanguageCode(languageCode);
        translation.setPagesBubbles(pagesBubblesJson);
        translation.setProjectTeamId(teamId);
        translation.setStatus(ChapterTranslationStatus.PUBLISHED);
        translation.setDeleted(false);
        chapterTranslationRepository.save(translation);
        log.info("[publishChapterFromTask] Saved ChapterTranslationEntity for chapterId={} languageCode={} teamId={}",
                savedChapter.getId(), languageCode, teamId);

        if (comic != null) {
            chapterCrudPlugin.evictChaptersCache(comic.getId());
        }
        chapterCrudPlugin.evictChapterDetailCache(savedChapter.getId());
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
}
