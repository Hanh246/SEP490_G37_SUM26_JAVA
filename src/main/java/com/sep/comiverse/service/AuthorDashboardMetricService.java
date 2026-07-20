package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.AuthorDashboardMetricsResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorDashboardMetricService {

    private static final int DEFAULT_MONTHS = 12;
    private static final int MAX_MONTHS = 24;
    private static final int TOP_COMIC_LIMIT = 5;
    private static final int RECENT_ACTIVITY_LIMIT = 6;

    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final ISubmissionRepository submissionRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;

    @Transactional(readOnly = true)
    public AuthorDashboardMetricsResponse getDashboardMetrics(UUID authorId, Integer requestedMonths) {
        if (authorId == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        int months = normalizeMonths(requestedMonths);
        List<ComicEntity> comics = comicRepository.findAllByAuthorIdAndDeletedFalseOrderByCreatedAtAsc(authorId);
        List<ChapterEntity> chapters = chapterRepository.findAllByComic_AuthorIdAndDeletedFalseOrderByCreatedAtAsc(authorId);
        List<SubmissionEntity> submissions = submissionRepository
                .findAllByAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(authorId, "author");
        List<ComicMetricSnapshotEntity> snapshots = metricSnapshotRepository
                .findAllByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId);

        Map<UUID, Integer> chapterCountByComic = chapters.stream()
                .filter(chapter -> chapter.getComic() != null && chapter.getComic().getId() != null)
                .collect(Collectors.groupingBy(
                        chapter -> chapter.getComic().getId(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        Map<UUID, ComicMetricSnapshotEntity> latestSnapshotByComic = snapshots.stream()
                .filter(snapshot -> snapshot.getComicId() != null)
                .collect(Collectors.toMap(
                        ComicMetricSnapshotEntity::getComicId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        return AuthorDashboardMetricsResponse.builder()
                .summary(buildSummary(comics, chapters, submissions, latestSnapshotByComic))
                .monthlyMetrics(buildMonthlyMetrics(months, comics, chapters, submissions, snapshots))
                .topComics(buildTopComics(comics, chapterCountByComic, latestSnapshotByComic))
                .recentActivities(buildRecentActivities(submissions))
                .generatedAt(Instant.now())
                .build();
    }

    private AuthorDashboardMetricsResponse.Summary buildSummary(
            List<ComicEntity> comics,
            List<ChapterEntity> chapters,
            List<SubmissionEntity> submissions,
            Map<UUID, ComicMetricSnapshotEntity> latestSnapshotByComic
    ) {
        long totalViews = comics.stream().mapToLong(comic -> defaultLong(comic.getViewCount())).sum();
        long totalFollowers = comics.stream().mapToLong(comic -> defaultLong(comic.getSaveCount())).sum();
        long totalLikes = comics.stream().mapToLong(comic -> defaultLong(comic.getLikeCount())).sum();
        long totalRatings = comics.stream().mapToLong(comic -> defaultLong(comic.getRatingCount())).sum();
        double weightedRating = comics.stream()
                .mapToDouble(comic -> defaultDouble(comic.getRatingAverage()) * defaultLong(comic.getRatingCount()))
                .sum();
        double averageRating = totalRatings == 0 ? 0.0 : weightedRating / totalRatings;

        long pendingReviews = submissions.stream().filter(this::isPending).count();
        long approvedReviews = submissions.stream().filter(this::isApproved).count();
        long rejectedReviews = submissions.stream().filter(this::isRejected).count();
        long decidedReviews = approvedReviews + rejectedReviews;
        double approvedRate = decidedReviews == 0 ? 0.0 : (approvedReviews * 100.0) / decidedReviews;

        BigDecimal estimatedRevenue = latestSnapshotByComic.values().stream()
                .map(ComicMetricSnapshotEntity::getEstimatedRevenue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AuthorDashboardMetricsResponse.Summary.builder()
                .totalComics((long) comics.size())
                .publishedComics(comics.stream().filter(this::isPublished).count())
                .draftComics(comics.stream().filter(comic -> !isPublished(comic)).count())
                .totalChapters((long) chapters.size())
                .totalViews(totalViews)
                .totalFollowers(totalFollowers)
                .totalLikes(totalLikes)
                .totalRatings(totalRatings)
                .averageRating(round(averageRating, 2))
                .pendingReviews(pendingReviews)
                .approvedRate(round(approvedRate, 2))
                .estimatedRevenue(estimatedRevenue)
                .build();
    }

    private List<AuthorDashboardMetricsResponse.MonthlyMetric> buildMonthlyMetrics(
            int months,
            List<ComicEntity> comics,
            List<ChapterEntity> chapters,
            List<SubmissionEntity> submissions,
            List<ComicMetricSnapshotEntity> snapshots
    ) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);

        Map<YearMonth, Map<UUID, ComicMetricSnapshotEntity>> latestSnapshotPerComicByMonth = new LinkedHashMap<>();
        snapshots.stream()
                .filter(snapshot -> snapshot.getComicId() != null && snapshot.getCreatedAt() != null)
                .sorted(Comparator.comparing(ComicMetricSnapshotEntity::getCreatedAt))
                .forEach(snapshot -> {
                    YearMonth month = toYearMonth(snapshot.getCreatedAt());
                    if (month.isBefore(firstMonth) || month.isAfter(currentMonth)) {
                        return;
                    }
                    latestSnapshotPerComicByMonth
                            .computeIfAbsent(month, ignored -> new LinkedHashMap<>())
                            .put(snapshot.getComicId(), snapshot);
                });

        List<AuthorDashboardMetricsResponse.MonthlyMetric> result = new ArrayList<>();
        for (int index = 0; index < months; index++) {
            YearMonth month = firstMonth.plusMonths(index);
            Map<UUID, ComicMetricSnapshotEntity> monthlySnapshots = latestSnapshotPerComicByMonth
                    .getOrDefault(month, Map.of());

            long views = monthlySnapshots.values().stream()
                    .mapToLong(snapshot -> defaultLong(snapshot.getViewCount()))
                    .sum();
            long followers = monthlySnapshots.values().stream()
                    .mapToLong(snapshot -> defaultLong(snapshot.getSavedCount()))
                    .sum();
            BigDecimal revenue = monthlySnapshots.values().stream()
                    .map(ComicMetricSnapshotEntity::getEstimatedRevenue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // The current month must show the freshest counters even when the scheduled
            // snapshot job has not run yet.
            if (month.equals(currentMonth)) {
                views = comics.stream().mapToLong(comic -> defaultLong(comic.getViewCount())).sum();
                followers = comics.stream().mapToLong(comic -> defaultLong(comic.getSaveCount())).sum();
            }

            long chaptersUploaded = chapters.stream()
                    .filter(chapter -> isInMonth(chapter.getCreatedAt(), month))
                    .count();
            long reviewsSubmitted = submissions.stream()
                    .filter(submission -> isInMonth(submission.getCreatedAt(), month))
                    .count();
            long chaptersApproved = submissions.stream()
                    .filter(submission -> submission.getChapterId() != null)
                    .filter(this::isApproved)
                    .filter(submission -> isInMonth(activityTime(submission), month))
                    .count();

            result.add(AuthorDashboardMetricsResponse.MonthlyMetric.builder()
                    .monthKey(month.toString())
                    .label(month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .views(views)
                    .followers(followers)
                    .estimatedRevenue(revenue)
                    .chaptersUploaded(chaptersUploaded)
                    .reviewsSubmitted(reviewsSubmitted)
                    .chaptersApproved(chaptersApproved)
                    .build());
        }
        return result;
    }

    private List<AuthorDashboardMetricsResponse.TopComic> buildTopComics(
            List<ComicEntity> comics,
            Map<UUID, Integer> chapterCountByComic,
            Map<UUID, ComicMetricSnapshotEntity> latestSnapshotByComic
    ) {
        return comics.stream()
                .sorted(Comparator
                        .comparingLong((ComicEntity comic) -> defaultLong(comic.getViewCount()))
                        .reversed()
                        .thenComparing(ComicEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_COMIC_LIMIT)
                .map(comic -> {
                    ComicMetricSnapshotEntity snapshot = latestSnapshotByComic.get(comic.getId());
                    return AuthorDashboardMetricsResponse.TopComic.builder()
                            .comicId(comic.getId())
                            .title(comic.getTitle())
                            .cover(comic.getCover())
                            .moderationStatus(comic.getModerationStatus() == null
                                    ? ComicModerationStatus.DRAFT.name()
                                    : comic.getModerationStatus().name())
                            .viewCount(defaultLong(comic.getViewCount()))
                            .followerCount(defaultLong(comic.getSaveCount()))
                            .likeCount(defaultLong(comic.getLikeCount()))
                            .chapterCount(chapterCountByComic.getOrDefault(comic.getId(), 0))
                            .ratingAverage(round(defaultDouble(comic.getRatingAverage()), 2))
                            .estimatedRevenue(snapshot == null || snapshot.getEstimatedRevenue() == null
                                    ? BigDecimal.ZERO
                                    : snapshot.getEstimatedRevenue())
                            .build();
                })
                .toList();
    }

    private List<AuthorDashboardMetricsResponse.RecentActivity> buildRecentActivities(
            List<SubmissionEntity> submissions
    ) {
        return submissions.stream()
                .sorted(Comparator.comparing(this::activityTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_ACTIVITY_LIMIT)
                .map(submission -> AuthorDashboardMetricsResponse.RecentActivity.builder()
                        .submissionId(submission.getId())
                        .comicId(submission.getComicId())
                        .chapterId(submission.getChapterId())
                        .title(submission.getTitle())
                        .description(buildActivityDescription(submission))
                        .status(normalizeStatus(submission.getStatus()))
                        .type(submission.getChapterId() == null ? "COMIC_REVIEW" : "CHAPTER_REVIEW")
                        .occurredAt(activityTime(submission))
                        .build())
                .toList();
    }

    private String buildActivityDescription(SubmissionEntity submission) {
        String target = submission.getChapterId() == null
                ? "Comic profile"
                : (submission.getChapter() == null ? "Chapter" : submission.getChapter());
        return target + " · " + toDisplayStatus(submission.getStatus());
    }

    private Instant activityTime(SubmissionEntity submission) {
        if (submission == null) {
            return null;
        }
        return submission.getUpdatedAt() != null ? submission.getUpdatedAt() : submission.getCreatedAt();
    }

    private boolean isInMonth(Instant instant, YearMonth month) {
        return instant != null && toYearMonth(instant).equals(month);
    }

    private YearMonth toYearMonth(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC));
    }

    private boolean isPublished(ComicEntity comic) {
        return comic != null && comic.getModerationStatus() == ComicModerationStatus.PUBLISHED;
    }

    private boolean isPending(SubmissionEntity submission) {
        return "pending".equals(normalizeStatus(submission == null ? null : submission.getStatus()));
    }

    private boolean isApproved(SubmissionEntity submission) {
        return "approved".equals(normalizeStatus(submission == null ? null : submission.getStatus()));
    }

    private boolean isRejected(SubmissionEntity submission) {
        return "rejected".equals(normalizeStatus(submission == null ? null : submission.getStatus()));
    }

    private String normalizeStatus(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String toDisplayStatus(String value) {
        String normalized = normalizeStatus(value);
        return switch (normalized) {
            case "pending" -> "Waiting for moderator";
            case "approved" -> "Approved";
            case "rejected" -> "Rejected";
            case "cancelled" -> "Cancelled";
            default -> "Updated";
        };
    }

    private int normalizeMonths(Integer requestedMonths) {
        if (requestedMonths == null) {
            return DEFAULT_MONTHS;
        }
        return Math.max(1, Math.min(MAX_MONTHS, requestedMonths));
    }

    private long defaultLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double defaultDouble(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
