package com.sep.comiverse.service;

import com.sep.comiverse.entity.UserLikeEntity;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserLikeService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserLikeRepository userLikeRepository;

    public boolean isComicLikedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;
        return userLikeRepository.existsByComicIdAndUserId(comicId, userId);
    }

    @Transactional
    public boolean toggleLikeComic(UUID comicId, UUID userId) {
        if(userId == null) return false;
        String comicIdStr = comicId.toString();
        boolean isLiked = isComicLikedByUser(comicId, userId);

        if (!isLiked) {
            UserLikeEntity like = UserLikeEntity.builder()
                    .comicId(comicId)
                    .userId(userId)
                    .build();
            userLikeRepository.save(like);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicIdStr, 1);
            return true;
        } else {
            userLikeRepository.findByComicIdAndUserId(comicId, userId)
                    .ifPresent(userLikeRepository::delete);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_LIKE_HASH, comicIdStr, -1);
            return false;
        }
    }

    public List<UUID> getLikedComicIds(UUID userId) {
        return userLikeRepository.findLikedComicIdsByUserId(userId);
    }

    public long getLikedComicCount(UUID userId) {
        if (userId == null) return 0;
        return userLikeRepository.countByUserId(userId);
    }
}

