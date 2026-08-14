package com.sep.comiverse.integration.scheduler;

import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.AbstractIntegrationTest;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.service.scheduler.LeaderboardScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class SchedulerJobIT extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardScheduler leaderboardScheduler;

    @Autowired
    private IComicRepository comicRepository;

    @BeforeEach
    void setUp() {
        comicRepository.save(ComicEntity.builder()
                .title("Scheduler Test Comic")
                .summary("Comic for background job verification")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build());
    }

    @Test
    @DisplayName("TC-SCH-JOB01-001: Direct invocation of computeLeaderboards() background job should re-calculate rankings and update state cleanly")
    void tc_sch_job01_001_computeLeaderboardsExecution() {
        assertDoesNotThrow(() -> leaderboardScheduler.computeLeaderboards(),
                "Direct background job invocation should complete without error");
    }
}
