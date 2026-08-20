package com.sep.comiverse.integration.scheduler;

import com.sep.comiverse.integration.support.AbstractBlackboxIT;
import com.sep.comiverse.service.scheduler.LeaderboardScheduler;
import com.sep.comiverse.service.scheduler.RecommendationScheduler;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerJobIT extends AbstractBlackboxIT {

    @Autowired
    private ViewSyncScheduler viewSyncScheduler;

    @Autowired
    private LeaderboardScheduler leaderboardScheduler;

    @Autowired
    private UserInteractionSyncScheduler userInteractionSyncScheduler;

    @Autowired
    private RecommendationScheduler recommendationScheduler;

    // ── JOB-01: Persist buffered reading activity and view counters ───────────

    @Test
    @DisplayName("TC-SCH-JOB01-001 [UC-01, UC-02]")
    void job01EmptyRedisBuffers() throws Exception {
        viewSyncScheduler.flushViewsToPostgres();
        getJson("/comics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB01-002 [UC-04, UC-32]")
    void job01CatalogStillReadable() throws Exception {
        viewSyncScheduler.flushViewsToPostgres();
        getJson("/comics/all")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── JOB-02: Clean up expired reading history logs ───────────────────────

    @Test
    @DisplayName("TC-SCH-JOB02-001 [UC-17, UC-18]")
    void job02NoExpiredHistory() throws Exception {
        viewSyncScheduler.cleanOldReadingHistories();
        getJson("/comics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB02-002 [UC-17]")
    void job02CatalogStillReadable() throws Exception {
        viewSyncScheduler.cleanOldReadingHistories();
        getJson("/comics/all")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── JOB-03: Compute comic view leaderboards ───────────────────────────────

    @Test
    @DisplayName("TC-SCH-JOB03-001 [UC-18, UC-43]")
    void job03LeaderboardDay() throws Exception {
        leaderboardScheduler.computeLeaderboards();
        getJson("/comics/leaderboard?timeframe=day")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB03-002 [UC-18, UC-43]")
    void job03LeaderboardWeek() throws Exception {
        leaderboardScheduler.computeLeaderboards();
        getJson("/comics/leaderboard?timeframe=week")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB03-003 [UC-19, UC-43]")
    void job03LeaderboardMonth() throws Exception {
        leaderboardScheduler.computeLeaderboards();
        getJson("/comics/leaderboard?timeframe=month")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── JOB-04: Synchronize user engagement and interaction metrics ─────────

    @Test
    @DisplayName("TC-SCH-JOB04-001 [UC-05, UC-06]")
    void job04EmptyEngagementCounters() throws Exception {
        userInteractionSyncScheduler.flushInteractionsToPostgres();
        getJson("/comics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB04-002 [UC-36, UC-43]")
    void job04ComicListStillReadable() throws Exception {
        userInteractionSyncScheduler.flushInteractionsToPostgres();
        getJson("/comics/all")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── JOB-05: Generate missing comic embedding vectors ──────────────────────

    @Test
    @DisplayName("TC-SCH-JOB05-001 [UC-14]")
    void job05NoMissingEmbeddings() throws Exception {
        recommendationScheduler.processNewComicsEmbeddings();
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB05-002 [UC-14]")
    void job05RecommendationsApiOk() throws Exception {
        recommendationScheduler.processNewComicsEmbeddings();
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── JOB-06: Recalculate user preference vectors ───────────────────────────

    @Test
    @DisplayName("TC-SCH-JOB06-001 [UC-06, UC-07]")
    void job06NoPendingUsers() throws Exception {
        recommendationScheduler.processUserPreferences();
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC-SCH-JOB06-002 [UC-10, UC-20]")
    void job06RecommendationsApiOk() throws Exception {
        recommendationScheduler.processUserPreferences();
        getJson("/comics/recommendations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
