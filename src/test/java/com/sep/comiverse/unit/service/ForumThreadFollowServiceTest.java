package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.ForumThreadFollowResponse;
import com.sep.comiverse.entity.ForumThreadFollowEntity;
import com.sep.comiverse.repository.IForumThreadFollowRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.service.ForumThreadFollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumThreadFollowServiceTest {

    @Mock
    private IForumThreadRepository forumThreadRepository;

    @Mock
    private IForumThreadFollowRepository forumThreadFollowRepository;

    private ForumThreadFollowService service;

    @BeforeEach
    void setUp() {
        service = new ForumThreadFollowService(forumThreadRepository, forumThreadFollowRepository);
    }

    @Test
    void toggleCreatesAStoredFollow() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(forumThreadRepository.existsById(threadId)).thenReturn(true);
        when(forumThreadFollowRepository.findByUserIdAndThreadId(userId, threadId))
                .thenReturn(Optional.empty());

        ForumThreadFollowResponse result = service.toggle(threadId, userId);

        assertTrue(result.isFollowing());
        verify(forumThreadFollowRepository).save(any(ForumThreadFollowEntity.class));
    }

    @Test
    void toggleDeletesAnExistingFollow() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ForumThreadFollowEntity existing = ForumThreadFollowEntity.builder()
                .threadId(threadId)
                .userId(userId)
                .build();
        when(forumThreadRepository.existsById(threadId)).thenReturn(true);
        when(forumThreadFollowRepository.findByUserIdAndThreadId(userId, threadId))
                .thenReturn(Optional.of(existing));

        ForumThreadFollowResponse result = service.toggle(threadId, userId);

        assertFalse(result.isFollowing());
        verify(forumThreadFollowRepository).delete(existing);
    }
}
