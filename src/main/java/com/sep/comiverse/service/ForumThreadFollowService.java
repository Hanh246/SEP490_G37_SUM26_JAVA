package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.ForumThreadFollowResponse;
import com.sep.comiverse.entity.ForumThreadFollowEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumThreadFollowRepository;
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
public class ForumThreadFollowService {

    private final IForumThreadRepository forumThreadRepository;
    private final IForumThreadFollowRepository forumThreadFollowRepository;

    @Transactional
    public ForumThreadFollowResponse toggle(UUID threadId, UUID userId) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
        if (!forumThreadRepository.existsById(threadId)) {
            throw new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND);
        }

        Optional<ForumThreadFollowEntity> existing =
                forumThreadFollowRepository.findByUserIdAndThreadId(userId, threadId);
        boolean following;
        if (existing.isPresent()) {
            forumThreadFollowRepository.delete(existing.get());
            following = false;
        } else {
            forumThreadFollowRepository.save(ForumThreadFollowEntity.builder()
                    .userId(userId)
                    .threadId(threadId)
                    .build());
            following = true;
        }
        return ForumThreadFollowResponse.builder().following(following).build();
    }

    @Transactional(readOnly = true)
    public Set<UUID> findFollowedThreadIds(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(forumThreadFollowRepository.findFollowedThreadIdsByUserId(userId));
    }
}
