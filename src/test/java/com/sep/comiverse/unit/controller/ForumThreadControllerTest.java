package com.sep.comiverse.unit.controller;

import com.sep.comiverse.controller.ForumThreadController;
import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.request.ReportForumThreadRequest;
import com.sep.comiverse.dto.response.ForumThreadFollowResponse;
import com.sep.comiverse.dto.response.ForumThreadLikeResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ForumThreadCrudPlugin;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.ForumThreadFollowService;
import com.sep.comiverse.service.ForumThreadLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumThreadControllerTest {

    @Mock
    private ForumThreadCrudPlugin crudPlugin;

    @Mock
    private IForumThreadRepository forumThreadRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private ForumThreadLikeService forumThreadLikeService;

    @Mock
    private ForumThreadFollowService forumThreadFollowService;

    private ForumThreadController controller;

    @BeforeEach
    void setUp() {
        controller = new ForumThreadController(crudPlugin);
        ReflectionTestUtils.setField(controller, "forumThreadRepository", forumThreadRepository);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "jwtTokenUtil", jwtTokenUtil);
        ReflectionTestUtils.setField(controller, "forumThreadLikeService", forumThreadLikeService);
        ReflectionTestUtils.setField(controller, "forumThreadFollowService", forumThreadFollowService);
    }

    @Test
    void incrementViewUsesAtomicRepositoryUpdate() {
        UUID threadId = UUID.randomUUID();
        when(forumThreadRepository.incrementViews(threadId)).thenReturn(1);

        assertEquals(HttpStatus.OK, controller.incrementView(threadId).getStatusCode());

        verify(forumThreadRepository).incrementViews(threadId);
    }

    @Test
    void incrementViewRejectsMissingThread() {
        UUID threadId = UUID.randomUUID();
        when(forumThreadRepository.incrementViews(threadId)).thenReturn(0);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> controller.incrementView(threadId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    }

    @Test
    void toggleLikeReturnsPersistedUserStateAndCount() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ForumThreadLikeResponse response = ForumThreadLikeResponse.builder()
                .liked(true)
                .likes(7)
                .build();
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(userId);
        when(forumThreadLikeService.toggle(threadId, userId)).thenReturn(response);

        var result = controller.toggleLike(threadId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().getData().isLiked());
        assertEquals(7, result.getBody().getData().getLikes());
        verify(forumThreadLikeService).toggle(threadId, userId);
    }

    @Test
    void threadDetailRestoresCurrentUsersPersistedLikeState() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ForumThreadDTO thread = new ForumThreadDTO();
        thread.setId(threadId);
        thread.setAuthorId(authorId);
        thread.setLikes(9);
        UserEntity author = UserEntity.builder()
                .avatarUrl("https://cdn.comiverse.test/avatar.png")
                .build();
        author.setId(authorId);
        when(crudPlugin.read(threadId)).thenReturn(Optional.of(thread));
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(userId);
        when(forumThreadLikeService.findLikedThreadIds(userId)).thenReturn(Set.of(threadId));
        when(forumThreadFollowService.findFollowedThreadIds(userId)).thenReturn(Set.of(threadId));
        when(userRepository.findAllById(Set.of(authorId))).thenReturn(List.of(author));

        var result = controller.findById(threadId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(9, result.getBody().getData().getLikes());
        assertTrue(result.getBody().getData().getIsLikedByCurrentUser());
        assertTrue(result.getBody().getData().getIsFollowedByCurrentUser());
        assertEquals("https://cdn.comiverse.test/avatar.png", result.getBody().getData().getAvatarUrl());
    }

    @Test
    void toggleFollowReturnsPersistedUserState() {
        UUID threadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(userId);
        when(forumThreadFollowService.toggle(threadId, userId)).thenReturn(
                ForumThreadFollowResponse.builder().following(true).build()
        );

        var result = controller.toggleFollow(threadId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().getData().isFollowing());
        verify(forumThreadFollowService).toggle(threadId, userId);
    }

    @Test
    void reportThreadOnlyChangesModerationReportFields() {
        UUID threadId = UUID.randomUUID();
        ForumThreadEntity thread = ForumThreadEntity.builder()
                .title("Keep this title")
                .isPinned(true)
                .isLocked(false)
                .build();
        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        ReportForumThreadRequest request = new ReportForumThreadRequest();
        request.setReason("  Spam link  ");

        controller.reportThread(threadId, request);

        assertTrue(thread.getIsReported());
        assertEquals("Spam link", thread.getReportReason());
        assertEquals("Keep this title", thread.getTitle());
        assertTrue(thread.getIsPinned());
        verify(forumThreadRepository).save(thread);
    }

    @Test
    void deleteThreadRejectsNonOwnerReader() {
        UUID threadId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        ForumThreadDTO thread = new ForumThreadDTO();
        thread.setId(threadId);
        thread.setAuthorId(UUID.randomUUID());
        thread.setTitle("Protected thread");
        UserEntity reader = UserEntity.builder()
                .role(RoleEntity.builder().roleName("READER").build())
                .email("reader@example.com")
                .build();
        when(crudPlugin.read(threadId)).thenReturn(Optional.of(thread));
        when(jwtTokenUtil.getCurrentUserId()).thenReturn(readerId);
        when(userRepository.findByIdWithRole(readerId)).thenReturn(Optional.of(reader));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> controller.delete(threadId)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(crudPlugin, never()).delete(threadId);
    }

    @Test
    void genericThreadUpdateIsRestrictedToModeratorsAndAdmins() throws Exception {
        Method update = ForumThreadController.class.getMethod("update", UUID.class, ForumThreadDTO.class);
        PreAuthorize preAuthorize = update.getAnnotation(PreAuthorize.class);

        assertEquals("hasAnyAuthority('MODERATOR', 'ADMIN')", preAuthorize.value());
    }
}
