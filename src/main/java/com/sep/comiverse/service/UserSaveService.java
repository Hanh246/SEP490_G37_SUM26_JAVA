package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserSaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSaveService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserSaveRepository userSaveRepository;

    public static final String COMIC_SAVE_USERS_SET_PREFIX = "comic:save:users:";
    public static final String COMIC_SAVE_HASH = "comic:save:counter";
    public static final String COMIC_SAVE_SYNC_ADD = "comic:save:sync:add";
    public static final String COMIC_SAVE_SYNC_REMOVE = "comic:save:sync:remove";

    public boolean isComicSavedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;

        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        // 1. Check if it is pending removal
        Boolean isPendingRemove = redisTemplate.opsForSet().isMember(COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
        if (Boolean.TRUE.equals(isPendingRemove)) {
            return false;
        }

        // 2. Check inside the temporary Redis buffer
        Boolean isSavedInRedis = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);
        if (Boolean.TRUE.equals(isSavedInRedis)) {
            return true;
        }

        // 3. Check if it is pending addition
        Boolean isPendingAdd = redisTemplate.opsForSet().isMember(COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
        if (Boolean.TRUE.equals(isPendingAdd)) {
            return true;
        }

        // 4. Check the database
        boolean isSavedInDb = userSaveRepository.existsByComicIdAndUserId(comicId, userId);
        if (isSavedInDb) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            return true;
        }
        return false;
    }

    public boolean toggleSaveComic(UUID comicId, UUID userId) {
        String comicIdStr = comicId.toString();
        String userSetKey = COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        boolean isSaved = isComicSavedByUser(comicId, userId);

        if (!isSaved) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, 1);

            redisTemplate.opsForSet().add(COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            return true;
        } else {
            redisTemplate.opsForSet().remove(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(COMIC_SAVE_HASH, comicIdStr, -1);

            redisTemplate.opsForSet().add(COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            return false;
        }
    }
}
