package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationService recommendationService;
    private final IComicRepository comicRepository;
    private final IUserRepository userRepository;

    // Runs every 1 hour (at minute 0) to process newly added comics missing embeddings
    @Scheduled(cron = "${recommendation.scheduler.comic-vector-cron:0 0 * * * *}")
    public void processNewComicsEmbeddings() {
        log.info("Starting background job to generate missing comic embeddings.");
        List<ComicEntity> missing = comicRepository.findComicsMissingVector();
        if (missing == null || missing.isEmpty()) {
            log.info("No comics missing embeddings found.");
            return;
        }

        log.info("Found {} comics missing embeddings. Processing...", missing.size());
        for (ComicEntity comic : missing) {
            try {
                recommendationService.updateComicVector(comic.getId());
            } catch (Exception e) {
                log.error("Failed to generate embedding for comic id " + comic.getId() + ": " + e.getMessage(), e);
            }
        }
        log.info("Finished background job for comic embeddings.");
    }

    // Runs every 1 hour (at minute 30) to recalculate user preference vectors offline
    @Scheduled(cron = "${recommendation.scheduler.user-vector-cron:0 30 * * * *}")
    public void processUserPreferences() {
        log.info("Starting background job to recalculate user preference vectors.");
        List<UUID> userIds = userRepository.findUserIdsWithPendingVectorUpdate();
        if (userIds == null || userIds.isEmpty()) {
            log.info("No active users with interactions found for vector recalculation.");
            return;
        }

        log.info("Found {} users with interactions to update. Processing...", userIds.size());
        for (UUID userId : userIds) {
            try {
                recommendationService.generateUserPreferenceVector(userId);
            } catch (Exception e) {
                log.error("Failed to generate preference vector for user id " + userId + ": " + e.getMessage(), e);
            }
        }
        log.info("Finished background job for user preference vectors.");
    }
}
