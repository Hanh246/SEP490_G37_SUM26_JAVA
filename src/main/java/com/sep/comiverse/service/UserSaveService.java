package com.sep.comiverse.service;

import com.sep.comiverse.repository.IUserSaveRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSaveService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserSaveRepository userSaveRepository;



    public boolean isComicSavedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;

        String comicIdStr = comicId.toString();
        String userSetKey = UserInteractionSyncScheduler.COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        // 1. Check if it is pending removal
        Boolean isPendingRemove = redisTemplate.opsForSet().isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
        if (Boolean.TRUE.equals(isPendingRemove)) {
            return false;
        }

        // 2. Check inside the temporary Redis buffer
        Boolean isSavedInRedis = redisTemplate.opsForSet().isMember(userSetKey, userIdStr);
        if (Boolean.TRUE.equals(isSavedInRedis)) {
            return true;
        }

        // 3. Check if it is pending addition
        Boolean isPendingAdd = redisTemplate.opsForSet().isMember(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
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
        String userSetKey = UserInteractionSyncScheduler.COMIC_SAVE_USERS_SET_PREFIX + comicIdStr;
        String userIdStr = userId.toString();

        boolean isSaved = isComicSavedByUser(comicId, userId);

        if (!isSaved) {
            redisTemplate.opsForSet().add(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr, 1);

            redisTemplate.opsForSet().add(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            return true;
        } else {
            redisTemplate.opsForSet().remove(userSetKey, userIdStr);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr, -1);

            redisTemplate.opsForSet().add(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_REMOVE, comicIdStr + ":" + userIdStr);
            redisTemplate.opsForSet().remove(UserInteractionSyncScheduler.COMIC_SAVE_SYNC_ADD, comicIdStr + ":" + userIdStr);
            return false;
        }
    }
}
