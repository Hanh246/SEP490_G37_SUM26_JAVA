package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.ForumCommentService;
import com.sep.comiverse.service.NotificationService;

import com.sep.comiverse.dto.ForumCommentDTO;
import com.sep.comiverse.dto.request.CreateForumCommentRequest;
import com.sep.comiverse.entity.ForumCommentEntity;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumCommentRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.repository.IForumThreadFollowRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumCommentServiceTest {

    @Mock
    private IForumCommentRepository forumCommentRepository;

    @Mock
    private IForumThreadRepository forumThreadRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.sep.comiverse.repository.IForumCommentLikeRepository forumCommentLikeRepository;

    @Mock
    private IForumThreadFollowRepository forumThreadFollowRepository;

    private ForumCommentService forumCommentService;

    private final UUID threadId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        forumCommentService = new ForumCommentService(
                forumCommentRepository,
                forumThreadRepository,
                userRepository,
                notificationService,
                forumCommentLikeRepository,
                forumThreadFollowRepository
        );
    }

    @Test
    void createTopLevelCommentNotifiesThreadOwnerWithDeepLink() {
        ForumThreadEntity thread = thread(ownerId);
        UserEntity actor = user(actorId, "Reply User");
        UUID savedId = UUID.randomUUID();
        CreateForumCommentRequest request = request("A useful reply", null);

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(actor));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        ForumCommentDTO result = forumCommentService.createComment(threadId, request, actorId);

        assertEquals(savedId, result.getId());
        assertEquals(1, thread.getReplies());
        verify(notificationService).notifyUser(
                ownerId,
                "New reply to your forum post",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
    }

    @Test
    void createReplyNotifiesCommentAuthorInsteadOfThreadOwner() {
        UUID commentOwnerId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID savedId = UUID.randomUUID();
        ForumThreadEntity thread = thread(ownerId);
        ForumCommentEntity parent = ForumCommentEntity.builder()
                .threadId(threadId)
                .userId(commentOwnerId)
                .content("Parent")
                .build();
        parent.setId(parentId);

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Reply User")));
        when(forumCommentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        ForumCommentDTO result = forumCommentService.createComment(
                threadId,
                request("Nested reply", parentId),
                actorId
        );

        assertEquals(parentId, result.getParentId());
        verify(notificationService).notifyUser(
                commentOwnerId,
                "New reply to your forum comment",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
    }

    @Test
    void createTopLevelCommentResolvesLegacyThreadOwnerByStoredAuthor() {
        UUID savedId = UUID.randomUUID();
        ForumThreadEntity thread = thread(null);
        thread.setAuthor("legacy-owner");
        UserEntity owner = user(ownerId, "Legacy Owner");

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Reply User")));
        when(userRepository.findByUsername("legacy-owner")).thenReturn(Optional.of(owner));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        forumCommentService.createComment(threadId, request("Reply to a legacy thread", null), actorId);

        assertEquals(ownerId, thread.getAuthorId());
        verify(notificationService).notifyUser(
                ownerId,
                "New reply to your forum post",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
    }

    @Test
    void createTopLevelCommentResolvesLegacyThreadOwnerByUniqueFullName() {
        UUID savedId = UUID.randomUUID();
        ForumThreadEntity thread = thread(null);
        thread.setAuthor("Legacy Owner");
        UserEntity owner = user(ownerId, "Legacy Owner");

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Reply User")));
        when(userRepository.findByUsername("Legacy Owner")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameOrFullNameIgnoreCase("Legacy Owner")).thenReturn(List.of(owner));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        forumCommentService.createComment(threadId, request("Reply to a legacy thread", null), actorId);

        assertEquals(ownerId, thread.getAuthorId());
        verify(notificationService).notifyUser(
                ownerId,
                "New reply to your forum post",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
    }

    @Test
    void createCommentDoesNotNotifyActorAboutOwnPost() {
        ForumThreadEntity thread = thread(actorId);
        UUID savedId = UUID.randomUUID();

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Owner")));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        ForumCommentDTO result = forumCommentService.createComment(
                threadId,
                request("Replying to myself", null),
                actorId
        );

        assertNotNull(result);
        verify(notificationService, never()).notifyUser(
                any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(NotificationPreferenceKey.FORUM_ACTIVITY)
        );
    }

    @Test
    void createCommentNotifiesFollowersWithoutDuplicatingTheDirectRecipient() {
        UUID followerId = UUID.randomUUID();
        UUID savedId = UUID.randomUUID();
        ForumThreadEntity thread = thread(ownerId);
        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Reply User")));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));
        when(forumThreadFollowRepository.findFollowerUserIdsByThreadId(threadId))
                .thenReturn(List.of(ownerId, followerId, actorId));

        forumCommentService.createComment(threadId, request("A tracked reply", null), actorId);

        verify(notificationService).notifyUser(
                ownerId,
                "New reply to your forum post",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
        verify(notificationService).notifyUser(
                followerId,
                "New activity in a followed thread",
                "Reply User replied in \"Thread title\".",
                "FORUM",
                "/forum/thread/" + threadId + "?comment=" + savedId,
                NotificationPreferenceKey.FORUM_ACTIVITY
        );
    }

    @Test
    void createCommentRejectsRepliesWhenThreadIsLocked() {
        ForumThreadEntity thread = thread(ownerId);
        thread.setIsLocked(true);
        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));

        CustomException exception = assertThrows(CustomException.class, () ->
                forumCommentService.createComment(threadId, request("Blocked reply", null), actorId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertEquals("This discussion thread is locked", exception.getMessage());
        verify(forumCommentRepository, never()).save(any());
    }

    @Test
    void createCommentKeepsUploadedImageUrl() {
        String content = "<br><img src=\"https://res.cloudinary.com/comiverse/forum/test.png\" alt=\"Attached Image\">";
        UUID savedId = UUID.randomUUID();
        ForumThreadEntity thread = thread(actorId);
        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Owner")));
        when(forumCommentRepository.save(any(ForumCommentEntity.class)))
                .thenAnswer(invocation -> savedComment(invocation.getArgument(0), savedId));

        ForumCommentDTO result = forumCommentService.createComment(
                threadId,
                request(content, null),
                actorId
        );

        assertEquals(content, result.getContent());
    }

    @Test
    void createReplyRejectsParentFromAnotherThread() {
        UUID parentId = UUID.randomUUID();
        ForumCommentEntity parent = ForumCommentEntity.builder()
                .threadId(UUID.randomUUID())
                .userId(ownerId)
                .content("Other thread")
                .build();
        parent.setId(parentId);

        when(forumThreadRepository.findById(threadId)).thenReturn(Optional.of(thread(ownerId)));
        when(userRepository.findByIdWithRole(actorId)).thenReturn(Optional.of(user(actorId, "Reply User")));
        when(forumCommentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        CustomException exception = assertThrows(CustomException.class, () ->
                forumCommentService.createComment(threadId, request("Invalid reply", parentId), actorId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Parent comment belongs to a different thread", exception.getMessage());
        verify(forumCommentRepository, never()).save(any());
    }

    private ForumThreadEntity thread(UUID authorId) {
        ForumThreadEntity thread = ForumThreadEntity.builder()
                .title("Thread title")
                .author("Owner")
                .authorId(authorId)
                .isLocked(false)
                .replies(0)
                .build();
        thread.setId(threadId);
        return thread;
    }

    private UserEntity user(UUID id, String fullName) {
        UserEntity user = UserEntity.builder()
                .username("user-" + id)
                .fullName(fullName)
                .email(id + "@example.com")
                .build();
        user.setId(id);
        return user;
    }

    private CreateForumCommentRequest request(String content, UUID parentId) {
        CreateForumCommentRequest request = new CreateForumCommentRequest();
        request.setContent(content);
        request.setParentId(parentId);
        return request;
    }

    private ForumCommentEntity savedComment(ForumCommentEntity comment, UUID id) {
        comment.setId(id);
        comment.setCreatedAt(Instant.now());
        return comment;
    }
}
