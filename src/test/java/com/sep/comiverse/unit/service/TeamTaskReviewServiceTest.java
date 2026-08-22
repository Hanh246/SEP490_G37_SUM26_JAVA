package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterTranslationEntity;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IReviewCommentRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.service.ReportService;
import com.sep.comiverse.service.TeamTaskReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamTaskReviewServiceTest {

    @Mock private ITeamTaskRepository taskRepository;
    @Mock private IPageTranslationRepository pageTranslationRepository;
    @Mock private IProjectTeamRepository projectTeamRepository;
    @Mock private IChapterRepository chapterRepository;
    @Mock private IComicRepository comicRepository;
    @Mock private IChapterTranslationRepository chapterTranslationRepository;
    @Mock private IReviewCommentRepository reviewCommentRepository;
    @Mock private ChapterCrudPlugin chapterCrudPlugin;
    @Mock private ReportService reportService;

    private TeamTaskReviewService service;

    @BeforeEach
    void setUp() {
        service = new TeamTaskReviewService(
                taskRepository,
                pageTranslationRepository,
                projectTeamRepository,
                chapterRepository,
                comicRepository,
                chapterTranslationRepository,
                reviewCommentRepository,
                chapterCrudPlugin,
                new ObjectMapper(),
                reportService
        );
    }

    @Test
    void approveAndPublishMarksLeaderReportsDoneForRevisionTask() {
        UUID translationId = stubPublishableTask("REVISION");

        verify(reportService).markAcceptedLeaderTranslationReportsDone(translationId);
    }

    @Test
    void approveAndPublishDoesNotMarkReportsDoneForRegularTask() {
        stubPublishableTask("REGULAR");

        verify(reportService, never()).markAcceptedLeaderTranslationReportsDone(any());
    }

    private UUID stubPublishableTask(String taskType) {
        UUID taskId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID translationId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        ChapterEntity chapter = ChapterEntity.builder()
                .chapterNumber("1")
                .build();
        chapter.setId(chapterId);

        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .projectTeamId(teamId)
                .chapter(chapter)
                .taskType(taskType)
                .status("under_review")
                .build();

        PageTranslationEntity page = PageTranslationEntity.builder()
                .pageNumber(1)
                .imageUrl("https://example.com/page.png")
                .bubbles("[]")
                .build();

        ChapterTranslationEntity savedTranslation = new ChapterTranslationEntity();
        savedTranslation.setId(translationId);

        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(taskId)).thenReturn(0L);
        when(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId)).thenReturn(List.of(page));
        when(chapterRepository.save(chapter)).thenReturn(chapter);
        when(projectTeamRepository.findById(teamId)).thenReturn(Optional.empty());
        when(chapterTranslationRepository.findByChapter_Id(chapterId)).thenReturn(List.of());
        when(chapterTranslationRepository.save(any(ChapterTranslationEntity.class))).thenReturn(savedTranslation);
        when(taskRepository.save(task)).thenReturn(task);

        service.approveAndPublish(task, reviewerId);
        return translationId;
    }
}
