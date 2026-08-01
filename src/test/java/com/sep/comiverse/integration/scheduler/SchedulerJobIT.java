package com.sep.comiverse.integration.scheduler;

import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.integration.support.ComiverseIntegrationTest;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.service.scheduler.LeaderboardScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sep.comiverse.integration.support.AbstractIntegrationTest;

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
    @DisplayName("TC-SCH-JOB01-001: Directly invoking computeLeaderboards() should process background rankings cleanly")
    void tc_sch_job01_001_computeLeaderboardsExecution() {
        assertDoesNotThrow(() -> leaderboardScheduler.computeLeaderboards(),
                "Direct background job invocation should complete without error");
    }
}
