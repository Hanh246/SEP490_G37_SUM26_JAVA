package com.sep.comiverse.unit.controller;

import com.sep.comiverse.controller.SubmissionController;

import com.sep.comiverse.entity.ProjectTeamEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.plugin.crud.SubmissionCrudPlugin;
import com.sep.comiverse.repository.IProjectTeamRepository;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerNotificationTest {

    @Mock
    private SubmissionCrudPlugin crudPlugin;
    @Mock
    private NotificationService notificationService;
    @Mock
    private IProjectTeamRepository projectTeamRepository;
    @Mock
    private IChapterRepository chapterRepository;
    @Mock
    private IComicRepository comicRepository;

    private SubmissionController controller;

    @BeforeEach
    void setUp() {
        controller = new SubmissionController(crudPlugin);
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        ReflectionTestUtils.setField(controller, "projectTeamRepository", projectTeamRepository);
        ReflectionTestUtils.setField(controller, "chapterRepository", chapterRepository);
        ReflectionTestUtils.setField(controller, "comicRepository", comicRepository);
    }

    @Test
    void authorSubmissionOutcomeUsesAuthorPreference() {
        UUID authorId = UUID.randomUUID();
        SubmissionEntity submission = SubmissionEntity.builder()
                .authorId(authorId)
                .queueType("author")
                .title("Author Comic")
                .build();

        ReflectionTestUtils.invokeMethod(controller, "notifySubmissionOwner", submission, true, null);

        verify(notificationService).notifyUser(
                eq(authorId),
                eq("Submission approved"),
                contains("Author Comic"),
                eq("UPDATE"),
                eq(NotificationPreferenceKey.SUBMISSION_STATUS)
        );
    }

    @Test
    void translatorSubmissionOutcomeUsesTeamUpdatePreference() {
        UUID leaderId = UUID.randomUUID();
        ProjectTeamEntity team = ProjectTeamEntity.builder()
                .title("Team Alpha")
                .leaderId(leaderId)
                .build();
        when(projectTeamRepository.findByTitleIgnoreCase("Team Alpha")).thenReturn(Optional.of(team));
        SubmissionEntity submission = SubmissionEntity.builder()
                .queueType("translator")
                .submittedBy("Team Alpha")
                .title("Translated Comic")
                .chapter("Chapter 2")
                .build();

        ReflectionTestUtils.invokeMethod(controller, "notifySubmissionOwner", submission, false, "Fix lettering");

        verify(notificationService).notifyUser(
                eq(leaderId),
                eq("Submission needs changes"),
                contains("Fix lettering"),
                eq("WARNING"),
                eq(NotificationPreferenceKey.TEAM_UPDATES)
        );
    }
    @Test
    void rejectingAuthorChapterPreservesPageUrlsAndStoresFeedback() {
        UUID chapterId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        ChapterEntity chapter = ChapterEntity.builder()
                .chapterNumber("2")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .images(List.of("https://cdn.test/001.png", "https://cdn.test/002.png"))
                .build();
        chapter.setId(chapterId);

        SubmissionEntity submission = SubmissionEntity.builder()
                .chapterId(chapterId)
                .queueType("author")
                .rejectionReason("Please fix page 1")
                .build();

        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(controller, "handleSubmissionRejected", submission, moderatorId);

        assertEquals(ChapterStatus.REJECTED, chapter.getModerationStatus());
        assertEquals("Please fix page 1", chapter.getRejectionReason());
        assertEquals(moderatorId, chapter.getRejectedById());
        assertEquals(List.of("https://cdn.test/001.png", "https://cdn.test/002.png"), chapter.getImages());
        verify(chapterRepository).save(chapter);
    }

    @Test
    void rejectingAuthorComicPreservesAllUnpublishedChapterPageUrlsAndFeedback() {
        UUID comicId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        ComicEntity comic = ComicEntity.builder()
                .title("Rejected Comic")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build();
        comic.setId(comicId);

        ChapterEntity rejectedChapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("1")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .images(List.of("https://cdn.test/ch1-001.png", "https://cdn.test/ch1-002.png"))
                .build();
        ChapterEntity publishedChapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("2")
                .moderationStatus(ChapterStatus.PUBLISHED)
                .images(List.of("https://cdn.test/ch2-001.png"))
                .build();

        SubmissionEntity submission = SubmissionEntity.builder()
                .comicId(comicId)
                .queueType("author")
                .rejectionReason("Comic-level feedback")
                .build();

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(chapterRepository.findAllByComic_IdAndDeletedFalse(comicId))
                .thenReturn(List.of(rejectedChapter, publishedChapter));
        when(comicRepository.save(any(ComicEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(controller, "handleSubmissionRejected", submission, moderatorId);

        assertEquals(ComicModerationStatus.REJECTED, comic.getModerationStatus());
        assertEquals("Comic-level feedback", comic.getRejectionReason());
        assertEquals(ChapterStatus.REJECTED, rejectedChapter.getModerationStatus());
        assertEquals("Comic-level feedback", rejectedChapter.getRejectionReason());
        assertEquals(moderatorId, rejectedChapter.getRejectedById());
        assertEquals(List.of("https://cdn.test/ch1-001.png", "https://cdn.test/ch1-002.png"), rejectedChapter.getImages());
        assertEquals(ChapterStatus.PUBLISHED, publishedChapter.getModerationStatus());
        assertEquals(List.of("https://cdn.test/ch2-001.png"), publishedChapter.getImages());
        verify(chapterRepository).save(rejectedChapter);
    }

    @Test
    void rejectingChapterRestoresImagesFromSubmissionEvidenceSnapshotWhenChapterRowIsEmpty() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        ComicEntity comic = ComicEntity.builder()
                .title("Evidence Comic")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build();
        comic.setId(comicId);

        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("3")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .images(List.of())
                .build();
        chapter.setId(chapterId);

        List<String> evidence = List.of(
                "https://cdn.test/ch3-001.png",
                "https://cdn.test/ch3-002.png",
                "https://cdn.test/ch3-003.png"
        );
        SubmissionEntity submission = SubmissionEntity.builder()
                .chapterId(chapterId)
                .queueType("author")
                .chapterImages(evidence)
                .pageCount(evidence.size())
                .rejectionReason("Fix bubbles")
                .build();

        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chapterRepository.findAllByComic_IdAndDeletedFalse(comicId)).thenReturn(List.of(chapter));
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(controller, "handleSubmissionRejected", submission, moderatorId);

        assertEquals(ChapterStatus.REJECTED, chapter.getModerationStatus());
        assertEquals(evidence, chapter.getImages());
        assertEquals("Fix bubbles", chapter.getRejectionReason());
    }

    @Test
    void rejectingOneChapterDoesNotAutoRejectComicWhileAnotherChapterIsSubmittedForReview() {
        UUID comicId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        ComicEntity comic = ComicEntity.builder()
                .title("Multi Chapter Comic")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build();
        comic.setId(comicId);

        ChapterEntity rejected = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("1")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .images(List.of("https://cdn.test/1.png"))
                .build();
        rejected.setId(rejectedId);

        ChapterEntity stillPending = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("2")
                .moderationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .images(List.of("https://cdn.test/2.png"))
                .build();
        stillPending.setId(pendingId);

        SubmissionEntity submission = SubmissionEntity.builder()
                .comicId(comicId)
                .chapterId(rejectedId)
                .queueType("author")
                .rejectionReason("Chapter 1 needs changes")
                .build();

        when(chapterRepository.findById(rejectedId)).thenReturn(Optional.of(rejected));
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterRepository.findAllByComic_IdAndDeletedFalse(comicId))
                .thenReturn(List.of(rejected, stillPending));

        ReflectionTestUtils.invokeMethod(controller, "handleSubmissionRejected", submission, moderatorId);

        assertEquals(ChapterStatus.REJECTED, rejected.getModerationStatus());
        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, stillPending.getModerationStatus());
        org.mockito.Mockito.verifyNoInteractions(comicRepository);
    }

    @Test
    void chapterPageCountTracksRetainedImages() {
        ChapterEntity chapter = ChapterEntity.builder()
                .images(List.of("p1", "p2", "p3", "p4"))
                .build();

        ReflectionTestUtils.invokeMethod(chapter, "syncPageCount");

        assertEquals(4, chapter.getPageCount());
    }

}
