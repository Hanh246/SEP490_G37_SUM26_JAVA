package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.ForumThreadLikeResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.entity.ForumThreadLikeEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumThreadLikeRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForumThreadLikeService {

    private final IForumThreadRepository forumThreadRepository;
    private final IForumThreadLikeRepository forumThreadLikeRepository;

    @Transactional
    public ForumThreadLikeResponse toggle(UUID threadId, UUID userId) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        ForumThreadEntity thread = forumThreadRepository.findByIdForUpdate(threadId)
                .orElseThrow(() -> new CustomException(
                        404,
                        "Discussion thread not found",
                        HttpStatus.NOT_FOUND
                ));
        Optional<ForumThreadLikeEntity> existing =
                forumThreadLikeRepository.findByUserIdAndThreadId(userId, threadId);

        int currentLikes = Math.max(0, thread.getLikes() == null ? 0 : thread.getLikes());
        boolean liked;
        if (existing.isPresent()) {
            forumThreadLikeRepository.delete(existing.get());
            currentLikes = Math.max(0, currentLikes - 1);
            liked = false;
        } else {
            forumThreadLikeRepository.save(ForumThreadLikeEntity.builder()
                    .userId(userId)
                    .threadId(threadId)
                    .build());
            currentLikes += 1;
            liked = true;
        }

        thread.setLikes(currentLikes);
        forumThreadRepository.save(thread);
        return ForumThreadLikeResponse.builder()
                .liked(liked)
                .likes(currentLikes)
                .build();
    }

    @Transactional(readOnly = true)
    public Set<UUID> findLikedThreadIds(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(forumThreadLikeRepository.findLikedThreadIdsByUserId(userId));
    }
}
