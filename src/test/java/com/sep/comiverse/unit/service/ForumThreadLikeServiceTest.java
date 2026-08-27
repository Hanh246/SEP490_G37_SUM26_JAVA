package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.ForumThreadLikeResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.entity.ForumThreadLikeEntity;
import com.sep.comiverse.repository.IForumThreadLikeRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.service.ForumThreadLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumThreadLikeServiceTest {

    @Mock
    private IForumThreadRepository forumThreadRepository;

    @Mock
    private IForumThreadLikeRepository forumThreadLikeRepository;

    private ForumThreadLikeService service;

    @BeforeEach
    void setUp() {
        service = new ForumThreadLikeService(forumThreadRepository, forumThreadLikeRepository);
    }

    @Test
    void toggleCreatesLikeAndPersistsIncrementedCount() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ForumThreadEntity thread = ForumThreadEntity.builder().likes(2).build();
        when(forumThreadRepository.findByIdForUpdate(threadId)).thenReturn(Optional.of(thread));
        when(forumThreadLikeRepository.findByUserIdAndThreadId(userId, threadId))
                .thenReturn(Optional.empty());

        ForumThreadLikeResponse result = service.toggle(threadId, userId);

        assertTrue(result.isLiked());
        assertEquals(3, result.getLikes());
        assertEquals(3, thread.getLikes());
        verify(forumThreadLikeRepository).save(any(ForumThreadLikeEntity.class));
        verify(forumThreadLikeRepository, never()).delete(any(ForumThreadLikeEntity.class));
        verify(forumThreadRepository).save(thread);
    }

    @Test
    void toggleDeletesExistingLikeWithoutAllowingNegativeCount() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ForumThreadEntity thread = ForumThreadEntity.builder().likes(0).build();
        ForumThreadLikeEntity existing = ForumThreadLikeEntity.builder()
                .threadId(threadId)
                .userId(userId)
                .build();
        when(forumThreadRepository.findByIdForUpdate(threadId)).thenReturn(Optional.of(thread));
        when(forumThreadLikeRepository.findByUserIdAndThreadId(userId, threadId))
                .thenReturn(Optional.of(existing));

        ForumThreadLikeResponse result = service.toggle(threadId, userId);

        assertFalse(result.isLiked());
        assertEquals(0, result.getLikes());
        assertEquals(0, thread.getLikes());
        verify(forumThreadLikeRepository).delete(existing);
        verify(forumThreadRepository).save(thread);
    }
}
