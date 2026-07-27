package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.ComicRatingResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.UserRatingEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IUserRatingRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRatingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserRatingRepository userRatingRepository;
    private final IComicRepository comicRepository;
    private final ComicCrudPlugin comicCrudPlugin;

    @Transactional
    public ComicRatingResponse rateComic(UUID comicId, UUID userId, int score) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        if (score < 1 || score > 5) {
            throw new CustomException(400, "Rating score must be between 1 and 5", HttpStatus.BAD_REQUEST);
        }

        ComicEntity comic = comicRepository.findById(comicId)
                .orElseThrow(() -> new CustomException(404, "Comic not found", HttpStatus.NOT_FOUND));

        Optional<UserRatingEntity> existingOpt = userRatingRepository.findByComicIdAndUserId(comicId, userId);
        UserRatingEntity rating;
        int deltaCount = 0;
        double deltaSum = 0.0;

        if (existingOpt.isPresent()) {
            rating = existingOpt.get();
            boolean wasDeleted = Boolean.TRUE.equals(rating.getDeleted());
            int oldScore = (rating.getScore() != null) ? rating.getScore() : 0;

            if (wasDeleted) {
                deltaCount = 1;
                deltaSum = score;
            } else {
                deltaCount = 0;
                deltaSum = score - oldScore;
            }

            rating.setScore(score);
            rating.setDeleted(false);
        } else {
            rating = UserRatingEntity.builder()
                    .comicId(comicId)
                    .userId(userId)
                    .score(score)
                    .build();
            deltaCount = 1;
            deltaSum = score;
        }

        userRatingRepository.save(rating);

        String comicIdStr = comicId.toString();
        try {
            if (deltaCount != 0) {
                redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_RATING_COUNT_HASH, comicIdStr, deltaCount);
            }
            if (deltaSum != 0.0) {
                redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_RATING_SUM_HASH, comicIdStr, deltaSum);
            }
        } catch (Exception e) {
            log.error("Failed to update Redis rating hash counters for comicId: {}", comicIdStr, e);
        }

        return updateComicRatingMetricsAndEvictCache(comic, score);
    }

    @Transactional
    public ComicRatingResponse deleteRating(UUID comicId, UUID userId) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        ComicEntity comic = comicRepository.findById(comicId)
                .orElseThrow(() -> new CustomException(404, "Comic not found", HttpStatus.NOT_FOUND));

        UserRatingEntity rating = userRatingRepository.findByComicIdAndUserIdAndDeletedFalse(comicId, userId)
                .orElseThrow(() -> new CustomException(404, "Rating not found for this comic", HttpStatus.NOT_FOUND));

        int oldScore = (rating.getScore() != null) ? rating.getScore() : 0;
        rating.setDeleted(true);
        userRatingRepository.save(rating);

        String comicIdStr = comicId.toString();
        try {
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_RATING_COUNT_HASH, comicIdStr, -1);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_RATING_SUM_HASH, comicIdStr, -oldScore);
        } catch (Exception e) {
            log.error("Failed to update Redis rating hash counters on delete for comicId: {}", comicIdStr, e);
        }

        return updateComicRatingMetricsAndEvictCache(comic, null);
    }

    @Transactional(readOnly = true)
    public ComicRatingResponse getComicRating(UUID comicId, UUID userId) {
        ComicEntity comic = comicRepository.findById(comicId)
                .orElseThrow(() -> new CustomException(404, "Comic not found", HttpStatus.NOT_FOUND));

        String comicIdStr = comicId.toString();
        int baseCount = (comic.getRatingCount() != null) ? comic.getRatingCount() : 0;
        double baseAvg = (comic.getRatingAverage() != null) ? comic.getRatingAverage() : 0.0;
        double baseSum = baseAvg * baseCount;

        int deltaCount = 0;
        double deltaSum = 0.0;
        try {
            Number rawRatingCount = (Number) redisTemplate.opsForHash().get(UserInteractionSyncScheduler.COMIC_RATING_COUNT_HASH, comicIdStr);
            Number rawRatingSum = (Number) redisTemplate.opsForHash().get(UserInteractionSyncScheduler.COMIC_RATING_SUM_HASH, comicIdStr);
            if (rawRatingCount != null) deltaCount = rawRatingCount.intValue();
            if (rawRatingSum != null) deltaSum = rawRatingSum.doubleValue();
        } catch (Exception e) {
            // Ignore Redis errors
        }

        int effectiveCount = Math.max(0, baseCount + deltaCount);
        double effectiveSum = Math.max(0.0, baseSum + deltaSum);
        double calculatedAvg = (effectiveCount > 0) ? Math.round((effectiveSum / effectiveCount) * 10.0) / 10.0 : 0.0;
        double effectiveAvg = Math.min(5.0, Math.max(0.0, calculatedAvg));

        Integer userScore = null;
        if (userId != null) {
            userScore = userRatingRepository.findByComicIdAndUserIdAndDeletedFalse(comicId, userId)
                    .map(UserRatingEntity::getScore)
                    .orElse(null);
        }

        return ComicRatingResponse.builder()
                .comicId(comicId)
                .ratingAverage(effectiveAvg)
                .ratingCount(effectiveCount)
                .userScore(userScore)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UUID> getRatedComicIds(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return userRatingRepository.findRatedComicIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getRatedComicCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return userRatingRepository.countByUserId(userId);
    }

    private ComicRatingResponse updateComicRatingMetricsAndEvictCache(ComicEntity comic, Integer currentScore) {
        Double rawAvg = userRatingRepository.getAverageRatingByComicId(comic.getId());
        Integer count = userRatingRepository.getRatingCountByComicId(comic.getId());

        double calculatedAvg = (rawAvg != null) ? Math.round(rawAvg * 10.0) / 10.0 : 0.0;
        double avg = Math.min(5.0, Math.max(0.0, calculatedAvg));
        int ratingCount = (count != null) ? count : 0;

        comic.setRatingAverage(avg);
        comic.setRatingCount(ratingCount);
        comicRepository.save(comic);

        try {
            comicCrudPlugin.evictComicCache(comic.getId());
        } catch (Exception e) {
            log.error("Failed to evict comic cache for comicId: {}", comic.getId(), e);
        }

        return ComicRatingResponse.builder()
                .comicId(comic.getId())
                .ratingAverage(avg)
                .ratingCount(ratingCount)
                .userScore(currentScore)
                .build();
    }
}
