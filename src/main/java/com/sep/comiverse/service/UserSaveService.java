package com.sep.comiverse.service;

import com.sep.comiverse.entity.UserSaveEntity;
import com.sep.comiverse.repository.IUserSaveRepository;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSaveService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IUserSaveRepository userSaveRepository;

    public boolean isComicSavedByUser(UUID comicId, UUID userId) {
        if (userId == null) return false;
        return userSaveRepository.existsByComicIdAndUserId(comicId, userId);
    }

    @Transactional
    public boolean toggleSaveComic(UUID comicId, UUID userId) {
        String comicIdStr = comicId.toString();
        boolean isSaved = isComicSavedByUser(comicId, userId);

        if (!isSaved) {
            UserSaveEntity save = UserSaveEntity.builder()
                    .comicId(comicId)
                    .userId(userId)
                    .build();
            userSaveRepository.save(save);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr, 1);
            return true;
        } else {
            userSaveRepository.findByComicIdAndUserId(comicId, userId)
                    .ifPresent(userSaveRepository::delete);
            redisTemplate.opsForHash().increment(UserInteractionSyncScheduler.COMIC_SAVE_HASH, comicIdStr, -1);
            return false;
        }
    }

    public List<UUID> getSavedComicIds(UUID userId) {
        return userSaveRepository.findSavedComicIdsByUserId(userId);
    }
}

