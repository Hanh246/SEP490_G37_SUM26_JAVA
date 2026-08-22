package com.sep.comiverse.unit.service;

import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.ICreatorPayoutRequestRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.projection.ComicChapterCountProjection;
import com.sep.comiverse.service.AuthorDashboardMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorDashboardMetricServiceTest {

    @Mock private IComicRepository comicRepository;
    @Mock private IChapterRepository chapterRepository;
    @Mock private ISubmissionRepository submissionRepository;
    @Mock private IComicMetricSnapshotRepository snapshotRepository;
    @Mock private ICreatorPayoutRequestRepository payoutRequestRepository;
    private AuthorDashboardMetricService service;

    @BeforeEach
    void setUp() {
        service = new AuthorDashboardMetricService(
                comicRepository, chapterRepository, submissionRepository, snapshotRepository, payoutRequestRepository);
    }

    @Test
    void getDashboardMetrics_requiresAuthorId() {
        CustomException error = assertThrows(CustomException.class,
                () -> service.getDashboardMetrics(null, "WEEK"));
        assertEquals(400, error.getCode());
    }

    @Test
    void getDashboardMetrics_emptyData_returnsZeroSummaryAndPeriodSizedChart() {
        UUID authorId = UUID.randomUUID();
        stub(authorId, List.of(), List.of(), List.of(), List.of(), List.of(), BigDecimal.ZERO);

        var week = service.getDashboardMetrics(authorId, "WEEK");
        var month = service.getDashboardMetrics(authorId, "MONTH");
        var year = service.getDashboardMetrics(authorId, "YEAR");

        assertEquals(0L, week.getSummary().getTotalComics());
        assertEquals(0L, week.getSummary().getTotalViews());
        assertEquals(BigDecimal.ZERO, week.getSummary().getEstimatedRevenue());
        assertEquals(BigDecimal.ZERO, week.getSummary().getTotalPaid());
        assertEquals(7, week.getMonthlyMetrics().size());
        assertEquals(30, month.getMonthlyMetrics().size());
        assertEquals(12, year.getMonthlyMetrics().size());
        assertTrue(week.getTopComics().isEmpty());
        assertNotNull(week.getGeneratedAt());
    }

    @Test
    void getDashboardMetrics_aggregatesSummaryTopComicAndReviewRates() {
        UUID authorId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        ComicEntity comic = ComicEntity.builder()
                .title("Popular")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .viewCount(100L)
                .saveCount(20)
                .likeCount(15)
                .ratingCount(4)
                .ratingAverage(4.5)
                .build();
        comic.setId(comicId);
        comic.setCreatedAt(Instant.now().minusSeconds(3600));

        ChapterEntity chapter = ChapterEntity.builder().comic(comic).chapterNumber("1").build();
        chapter.setCreatedAt(Instant.now());

        SubmissionEntity pending = new SubmissionEntity();
        pending.setStatus("PENDING");
        pending.setAuthorId(authorId);
        pending.setComicId(comicId);
        pending.setCreatedAt(Instant.now());
        SubmissionEntity approved = new SubmissionEntity();
        approved.setStatus("APPROVED");
        approved.setAuthorId(authorId);
        approved.setComicId(comicId);
        approved.setChapterId(UUID.randomUUID());
        approved.setCreatedAt(Instant.now());
        SubmissionEntity rejected = new SubmissionEntity();
        rejected.setStatus("REJECTED");
        rejected.setAuthorId(authorId);
        rejected.setComicId(comicId);
        rejected.setCreatedAt(Instant.now());

        ComicMetricSnapshotEntity snapshot = new ComicMetricSnapshotEntity();
        snapshot.setComicId(comicId);
        snapshot.setViewCount(90L);
        snapshot.setSavedCount(18L);
        snapshot.setEstimatedRevenue(new BigDecimal("12.50"));
        snapshot.setCreatedAt(Instant.now().minusSeconds(60));

        ComicChapterCountProjection projection = org.mockito.Mockito.mock(ComicChapterCountProjection.class);
        when(projection.getComicId()).thenReturn(comicId);
        when(projection.getChapterCount()).thenReturn(1L);
        stub(authorId, List.of(comic), List.of(chapter), List.of(pending, approved, rejected),
                List.of(snapshot), List.of(projection), new BigDecimal("25.00"));

        var result = service.getDashboardMetrics(authorId, "WEEK");

        assertEquals(1L, result.getSummary().getTotalComics());
        assertEquals(1L, result.getSummary().getPublishedComics());
        assertEquals(100L, result.getSummary().getTotalViews());
        assertEquals(20L, result.getSummary().getTotalFollowers());
        assertEquals(4.5, result.getSummary().getAverageRating());
        assertEquals(1L, result.getSummary().getPendingReviews());
        assertEquals(50.0, result.getSummary().getApprovedRate());
        assertEquals(new BigDecimal("12.50"), result.getSummary().getEstimatedRevenue());
        assertEquals(new BigDecimal("25.00"), result.getSummary().getTotalPaid());
        assertEquals("Popular", result.getTopComics().get(0).getTitle());
        assertEquals(1, result.getTopComics().get(0).getChapterCount());
        assertFalse(result.getRecentActivities().isEmpty());
    }

    private void stub(
            UUID authorId,
            List<ComicEntity> comics,
            List<ChapterEntity> chapters,
            List<SubmissionEntity> submissions,
            List<ComicMetricSnapshotEntity> snapshots,
            List<ComicChapterCountProjection> counts,
            BigDecimal totalPaid
    ) {
        when(comicRepository.findAllByAuthorIdAndDeletedFalseOrderByCreatedAtAsc(authorId)).thenReturn(comics);
        when(chapterRepository.findAllByComic_AuthorIdAndDeletedFalseOrderByCreatedAtAsc(authorId)).thenReturn(chapters);
        when(submissionRepository.findAllByAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(authorId, "author"))
                .thenReturn(submissions);
        when(snapshotRepository.findAllByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId)).thenReturn(snapshots);
        when(payoutRequestRepository.sumPaidAmountUsdByUserIdAndRole(
                authorId, CreatorPayoutRole.AUTHOR, CreatorPayoutStatus.PAID
        )).thenReturn(totalPaid);
        when(chapterRepository.countChaptersByComicForAuthor(authorId)).thenReturn(counts);
    }
}
