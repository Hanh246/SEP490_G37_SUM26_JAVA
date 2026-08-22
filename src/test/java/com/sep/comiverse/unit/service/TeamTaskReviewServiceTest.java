package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.sep.comiverse.service.TeamTaskReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
                new ObjectMapper()
        );

        lenient().when(taskRepository.save(any(TeamTaskEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chapterRepository.save(any(ChapterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chapterTranslationRepository.save(any(ChapterTranslationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ===== returnToInProgress =====

    @Test
    void returnToInProgressUnderReviewTaskMovesBackAndClearsCompletedAt() {
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .status("under_review")
                .completedAt(Instant.now())
                .build();

        TeamTaskEntity result = service.returnToInProgress(task);

        assertEquals("in_progress", result.getStatus());
        assertNull(result.getCompletedAt());
        verify(taskRepository).save(task);
    }

    // ===== approveAndPublish: decision-table gates =====

    @Test
    void approveAndPublishRejectsWhenOneOpenReviewCommentExists() {
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .status("under_review")
                .build();

        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(task.getId()))
                .thenReturn(1L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.approveAndPublish(task, UUID.randomUUID())
        );

        assertEquals(409, error.getCode());
        assertEquals("under_review", task.getStatus());

        verify(pageTranslationRepository, never())
                .findByTaskId_IdOrderByPageNumberAsc(any());
        verify(pageTranslationRepository, never()).saveAll(anyList());
        verifyNoInteractions(
                projectTeamRepository,
                chapterRepository,
                comicRepository,
                chapterTranslationRepository,
                chapterCrudPlugin
        );
        verify(taskRepository, never()).save(any());
    }

    @Test
    void approveAndPublishRejectsTaskWithoutPages() {
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(UUID.randomUUID())
                .status("under_review")
                .build();

        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(task.getId()))
                .thenReturn(0L);
        when(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(task.getId()))
                .thenReturn(List.of());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.approveAndPublish(task, UUID.randomUUID())
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("no pages"));

        verify(pageTranslationRepository, never()).saveAll(anyList());
        verifyNoInteractions(
                projectTeamRepository,
                chapterRepository,
                comicRepository,
                chapterTranslationRepository,
                chapterCrudPlugin
        );
        verify(taskRepository, never()).save(any());
    }

    // ===== approveAndPublish: task/page state =====

    @Test
    void approveAndPublishValidTaskMovesTaskToCompleted() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        TeamTaskEntity result = service.approveAndPublish(f.task, f.reviewerId);

        assertEquals("completed", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNull(result.getRejectionReason());
        verify(taskRepository).save(f.task);
    }

    @Test
    void approveAndPublishCopiesCurrentBubblesToReviewBaselineForAllPages() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        assertEquals(f.page1.getBubbles(), f.page1.getReviewBaselineBubbles());
        assertEquals(f.page2.getBubbles(), f.page2.getReviewBaselineBubbles());
        verify(pageTranslationRepository).saveAll(List.of(f.page1, f.page2));
    }

    @Test
    void approveAndPublishTaskWithoutChapterCompletesTaskButSkipsPublication() {
        UUID taskId = UUID.randomUUID();
        TeamTaskEntity task = TeamTaskEntity.builder()
                .id(taskId)
                .status("under_review")
                .chapter(null)
                .build();
        PageTranslationEntity page = page(task, 1, "https://cdn/1.png", "[{\"text\":\"Xin chao\"}]");

        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(taskId)).thenReturn(0L);
        when(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId))
                .thenReturn(List.of(page));

        TeamTaskEntity result = service.approveAndPublish(task, UUID.randomUUID());

        assertEquals("completed", result.getStatus());
        assertEquals(page.getBubbles(), page.getReviewBaselineBubbles());
        verify(pageTranslationRepository).saveAll(List.of(page));
        verify(taskRepository).save(task);

        verifyNoInteractions(
                chapterRepository,
                projectTeamRepository,
                comicRepository,
                chapterTranslationRepository,
                chapterCrudPlugin
        );
    }

    // ===== chapter publication =====

    @Test
    void approveAndPublishPublishesChapterWithReviewerAudit() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        assertEquals(ChapterStatus.PUBLISHED, f.chapter.getModerationStatus());
        assertEquals(f.reviewerId, f.chapter.getApprovedById());
        assertNotNull(f.chapter.getApprovedAt());
        verify(chapterRepository).save(f.chapter);
    }

    // ===== team aggregate =====

    @Test
    void approveAndPublishIncrementsExistingTeamChapterCount() {
        Fixture f = fixture();
        f.team.setChaptersCount(2);
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        assertEquals(3, f.team.getChaptersCount());
        verify(projectTeamRepository).save(f.team);
    }

    @Test
    void approveAndPublishInitializesNullTeamChapterCountToOne() {
        Fixture f = fixture();
        f.team.setChaptersCount(null);
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        assertEquals(1, f.team.getChaptersCount());
        verify(projectTeamRepository).save(f.team);
    }

    @Test
    void approveAndPublishWithoutTeamUsesDefaultLanguageAndStillPublishesTranslation() {
        Fixture f = fixture();
        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.empty());
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED)).thenReturn(3L);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId)).thenReturn(List.of());

        service.approveAndPublish(f.task, f.reviewerId);

        ArgumentCaptor<ChapterTranslationEntity> captor =
                ArgumentCaptor.forClass(ChapterTranslationEntity.class);
        verify(chapterTranslationRepository).save(captor.capture());

        ChapterTranslationEntity translation = captor.getValue();
        assertEquals("vi", translation.getLanguageCode());
        assertEquals(f.teamId, translation.getProjectTeamId());
        assertEquals(ChapterTranslationStatus.PUBLISHED, translation.getStatus());
        verify(projectTeamRepository, never()).save(any());
    }

    // ===== comic aggregate/cache =====

    @Test
    void approveAndPublishUpdatesComicAggregates() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        assertEquals("5", f.comic.getLatestChapterNumber());
        assertNotNull(f.comic.getLastChapterUpdatedAt());
        assertEquals(3, f.comic.getChapterCount());
        verify(comicRepository).save(f.comic);
    }

    @Test
    void approveAndPublishEvictsComicAndChapterCachesAfterPublication() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        verify(chapterCrudPlugin).evictChaptersCache(f.comicId);
        verify(chapterCrudPlugin).evictChapterDetailCache(f.chapterId);
    }

    @ParameterizedTest
    @ValueSource(longs = {
            2147483646L,
            2147483647L,
            2147483648L
    })
    void approveAndPublishAppliesChapterCountBoundaryAtIntegerMax(long publishedCount) {
        Fixture f = fixture();
        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED))
                .thenReturn(publishedCount);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId)).thenReturn(List.of());

        service.approveAndPublish(f.task, f.reviewerId);

        int expected = publishedCount > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) publishedCount;
        assertEquals(expected, f.comic.getChapterCount());
    }

    @Test
    void approveAndPublishChapterWithoutComicSkipsComicUpdateAndStillEvictsChapterDetailCache() {
        Fixture f = fixture();
        f.chapter.setComic(null);

        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(f.taskId)).thenReturn(0L);
        when(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(f.taskId))
                .thenReturn(List.of(f.page1, f.page2));
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId)).thenReturn(List.of());

        service.approveAndPublish(f.task, f.reviewerId);

        verifyNoInteractions(comicRepository);
        verify(chapterCrudPlugin, never()).evictChaptersCache(any());
        verify(chapterCrudPlugin).evictChapterDetailCache(f.chapterId);
    }

    // ===== chapter translation create/update/language selection =====

    @Test
    void approveAndPublishCreatesNewTranslationWhenNoMatchingTranslationExists() {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        ArgumentCaptor<ChapterTranslationEntity> captor =
                ArgumentCaptor.forClass(ChapterTranslationEntity.class);
        verify(chapterTranslationRepository).save(captor.capture());

        ChapterTranslationEntity translation = captor.getValue();
        assertSame(f.chapter, translation.getChapter());
        assertEquals("vi", translation.getLanguageCode());
        assertEquals(f.teamId, translation.getProjectTeamId());
        assertEquals(ChapterTranslationStatus.PUBLISHED, translation.getStatus());
        assertFalse(Boolean.TRUE.equals(translation.getDeleted()));
    }

    @Test
    void approveAndPublishUpdatesExistingTranslationForMatchingLanguage() {
        Fixture f = fixture();
        ChapterTranslationEntity existing = new ChapterTranslationEntity();
        existing.setChapter(f.chapter);
        existing.setLanguageCode("vi");
        existing.setStatus(ChapterTranslationStatus.PUBLISHED);
        existing.setDeleted(true);

        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED)).thenReturn(3L);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId))
                .thenReturn(List.of(existing));

        service.approveAndPublish(f.task, f.reviewerId);

        verify(chapterTranslationRepository).save(existing);
        assertSame(f.chapter, existing.getChapter());
        assertEquals("vi", existing.getLanguageCode());
        assertEquals(f.teamId, existing.getProjectTeamId());
        assertEquals(ChapterTranslationStatus.PUBLISHED, existing.getStatus());
        assertFalse(Boolean.TRUE.equals(existing.getDeleted()));
        assertTrue(existing.getPagesBubbles().contains("Xin chao"));
    }

    @Test
    void approveAndPublishNormalizesLanguageBeforeMatchingExistingTranslation() {
        Fixture f = fixture();
        f.team.setTargetLang("Vietnamese");

        ChapterTranslationEntity existing = new ChapterTranslationEntity();
        existing.setChapter(f.chapter);
        existing.setLanguageCode("VI");
        existing.setStatus(ChapterTranslationStatus.PUBLISHED);
        existing.setDeleted(false);

        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED)).thenReturn(3L);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId))
                .thenReturn(List.of(existing));

        service.approveAndPublish(f.task, f.reviewerId);

        verify(chapterTranslationRepository).save(existing);
        assertEquals("vi", existing.getLanguageCode());
        assertEquals(f.teamId, existing.getProjectTeamId());
    }

    @Test
    void approveAndPublishIgnoresNonMatchingTranslationAndCreatesTargetLanguageTranslation() {
        Fixture f = fixture();
        ChapterTranslationEntity english = new ChapterTranslationEntity();
        english.setChapter(f.chapter);
        english.setLanguageCode("en");
        english.setStatus(ChapterTranslationStatus.PUBLISHED);

        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED)).thenReturn(3L);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId))
                .thenReturn(List.of(english));

        service.approveAndPublish(f.task, f.reviewerId);

        ArgumentCaptor<ChapterTranslationEntity> captor =
                ArgumentCaptor.forClass(ChapterTranslationEntity.class);
        verify(chapterTranslationRepository).save(captor.capture());

        ChapterTranslationEntity saved = captor.getValue();
        assertNotSame(english, saved);
        assertEquals("vi", saved.getLanguageCode());
    }

    @Test
    void approveAndPublishSerializesEveryPageWithExpectedJsonSchema() throws Exception {
        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        ArgumentCaptor<ChapterTranslationEntity> captor =
                ArgumentCaptor.forClass(ChapterTranslationEntity.class);
        verify(chapterTranslationRepository).save(captor.capture());

        JsonNode root = new ObjectMapper().readTree(captor.getValue().getPagesBubbles());

        assertTrue(root.isArray());
        assertEquals(2, root.size());

        JsonNode first = root.get(0);
        assertEquals(f.page1.getId().toString(), first.get("pageId").asText());
        assertEquals(1, first.get("pageNumber").asInt());
        assertEquals("https://cdn/1.png", first.get("imageUrl").asText());
        assertEquals(f.page1.getBubbles(), first.get("bubbles").asText());

        JsonNode second = root.get(1);
        assertEquals(f.page2.getId().toString(), second.get("pageId").asText());
        assertEquals(2, second.get("pageNumber").asInt());
        assertEquals("https://cdn/2.png", second.get("imageUrl").asText());
        assertEquals(f.page2.getBubbles(), second.get("bubbles").asText());
    }

    @Test
    void approveAndPublishFallsBackToEmptyJsonWhenPageSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialization failed"));

        service = new TeamTaskReviewService(
                taskRepository,
                pageTranslationRepository,
                projectTeamRepository,
                chapterRepository,
                comicRepository,
                chapterTranslationRepository,
                reviewCommentRepository,
                chapterCrudPlugin,
                failingMapper
        );

        Fixture f = fixture();
        stubHappyPublishInputs(f);

        service.approveAndPublish(f.task, f.reviewerId);

        ArgumentCaptor<ChapterTranslationEntity> captor =
                ArgumentCaptor.forClass(ChapterTranslationEntity.class);
        verify(chapterTranslationRepository).save(captor.capture());
        assertEquals("[]", captor.getValue().getPagesBubbles());
    }

    // ===== helpers =====

    private void stubHappyPublishInputs(Fixture f) {
        stubGateAndPages(f);
        when(projectTeamRepository.findById(f.teamId)).thenReturn(Optional.of(f.team));
        when(chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                f.comicId, ChapterStatus.PUBLISHED)).thenReturn(3L);
        when(chapterTranslationRepository.findByChapter_Id(f.chapterId)).thenReturn(List.of());
    }

    private void stubGateAndPages(Fixture f) {
        when(reviewCommentRepository.countByPage_TaskId_IdAndResolvedFalse(f.taskId)).thenReturn(0L);
        when(pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(f.taskId))
                .thenReturn(List.of(f.page1, f.page2));
    }

    private Fixture fixture() {
        Fixture f = new Fixture();
        f.taskId = UUID.randomUUID();
        f.teamId = UUID.randomUUID();
        f.chapterId = UUID.randomUUID();
        f.comicId = UUID.randomUUID();
        f.reviewerId = UUID.randomUUID();

        f.comic = ComicEntity.builder()
                .title("ComiVerse Test")
                .chapterCount(1)
                .build();
        f.comic.setId(f.comicId);

        f.chapter = ChapterEntity.builder()
                .comic(f.comic)
                .chapterNumber("5")
                .moderationStatus(ChapterStatus.PENDING_REVIEW)
                .build();
        f.chapter.setId(f.chapterId);

        f.task = TeamTaskEntity.builder()
                .id(f.taskId)
                .projectTeamId(f.teamId)
                .chapter(f.chapter)
                .status("under_review")
                .rejectionReason("old feedback")
                .build();

        f.page1 = page(f.task, 1, "https://cdn/1.png", "[{\"text\":\"Xin chao\"}]");
        f.page2 = page(f.task, 2, "https://cdn/2.png", "[{\"text\":\"Tam biet\"}]");

        f.team = ProjectTeamEntity.builder()
                .title("VI Team")
                .targetLang("vi")
                .chaptersCount(2)
                .build();
        f.team.setId(f.teamId);

        return f;
    }

    private PageTranslationEntity page(
            TeamTaskEntity task,
            int pageNumber,
            String imageUrl,
            String bubbles
    ) {
        PageTranslationEntity page = PageTranslationEntity.builder()
                .taskId(task)
                .pageNumber(pageNumber)
                .imageUrl(imageUrl)
                .bubbles(bubbles)
                .build();
        page.setId(UUID.randomUUID());
        return page;
    }

    private static class Fixture {
        UUID taskId;
        UUID teamId;
        UUID chapterId;
        UUID comicId;
        UUID reviewerId;
        ComicEntity comic;
        ChapterEntity chapter;
        TeamTaskEntity task;
        PageTranslationEntity page1;
        PageTranslationEntity page2;
        ProjectTeamEntity team;
    }
}
