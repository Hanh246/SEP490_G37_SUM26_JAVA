package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserLikeService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserLikeRepository userLikeRepository;

    public static final String COMIC_LIKE_USERS_SET_PREFIX = "comic:like:users:";
    public static final String COMIC_LIKE_HASH = "comic:like:counter";
    public static final String COMIC_LIKE_SYNC_ADD = "comic:like:sync:add";
    public static final String COMIC_LIKE_SYNC_REMOVE = "comic:like:sync:remove";

    public boolean isComicLikedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;

        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_LIKE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        // 1. Check if it is pending removal
        Boolean isPendingRemove = redisTemplate.opsForSet().isMember(COMIC_LIKE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
        if (Boolean.TRUE.equals(isPendingRemove)) {
            return false;
        }

        // 2. Check inside the temporary Redis buffer
        Boolean isLikedInRedis = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);
        if (Boolean.TRUE.equals(isLikedInRedis)) {
            return true;
        }

        // 3. Check if it is pending addition
        Boolean isPendingAdd = redisTemplate.opsForSet().isMember(COMIC_LIKE_SYNC_ADD, comicIdStr + ":" + userIdStr);
        if (Boolean.TRUE.equals(isPendingAdd)) {
            return true;
        }

        // 4. Check the database
        boolean isLikedInDb = userLikeRepository.existsByComicIdAndUserId(comicId, userId);
        if (isLikedInDb) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            return true;
        }
        return false;
    }

    public boolean toggleLikeComic(UUID comicId, UUID userId) {
        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_LIKE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        boolean isLiked = isComicLikedByUser(comicId, userId);

        if (!isLiked) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, 1);

            redisTemplate.opsForSet().add(COMIC_LIKE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(COMIC_LIKE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            return true;
        } else {
            redisTemplate.opsForSet().remove(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_LIKE_HASH, comicIdStr, -1);

            redisTemplate.opsForSet().add(COMIC_LIKE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(COMIC_LIKE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            return false;
        }
    }
}
