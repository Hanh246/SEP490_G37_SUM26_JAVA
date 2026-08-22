package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorDashboardMetricsResponse {
    private Summary summary;
    private List<MonthlyMetric> monthlyMetrics;
    private List<TopComic> topComics;
    private List<RecentActivity> recentActivities;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long totalComics;
        private Long publishedComics;
        private Long draftComics;
        private Long totalChapters;
        private Long totalViews;
        private Long totalFollowers;
        private Long totalLikes;
        private Long totalRatings;
        private Double averageRating;
        private Long pendingReviews;
        private Double approvedRate;
        private BigDecimal estimatedRevenue;
        private BigDecimal totalPaid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyMetric {
        private String monthKey;
        private String label;
        private Long views;
        private Long followers;
        private BigDecimal estimatedRevenue;
        private Long chaptersUploaded;
        private Long reviewsSubmitted;
        private Long chaptersApproved;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopComic {
        private UUID comicId;
        private String title;
        private String cover;
        private String moderationStatus;
        private Long viewCount;
        private Long followerCount;
        private Long likeCount;
        private Integer chapterCount;
        private Double ratingAverage;
        private BigDecimal estimatedRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private UUID submissionId;
        private UUID comicId;
        private UUID chapterId;
        private String title;
        private String description;
        private String status;
        private String type;
        private Instant occurredAt;
    }
}
