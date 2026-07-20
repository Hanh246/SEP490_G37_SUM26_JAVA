package com.sep.comiverse.service;

import com.sep.comiverse.dto.ChapterCommentDTO;
import com.sep.comiverse.dto.ComicCommentDTO;
import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.dto.request.CreateChapterCommentRequest;
import com.sep.comiverse.dto.request.CreateComicCommentRequest;
import com.sep.comiverse.entity.ChapterCommentEntity;
import com.sep.comiverse.entity.ComicCommentEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterCommentRepository;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicCommentRepository;
import com.sep.comiverse.repository.IComicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

    @Mock
    private IComicCommentRepository comicCommentRepository;

    @Mock
    private IChapterCommentRepository chapterCommentRepository;

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private IChapterRepository chapterRepository;

    @Mock
    private UserService userService;

    private CommentService commentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID comicId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                comicCommentRepository,
                chapterCommentRepository,
                comicRepository,
                chapterRepository,
                userService
        );
    }

    private UserSnapshot mockUserSnapshot() {
        UserSnapshot snapshot = new UserSnapshot();
        snapshot.setUserId(userId);
        snapshot.setUserName("testUser");
        snapshot.setAvatarURL("testAvatar");
        return snapshot;
    }

    @Test
    void testCreateComicComment_UserNull_ThrowsUnauthorized() {
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Test comment");

        CustomException exception = assertThrows(CustomException.class, () ->
                commentService.createComicComment(request, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals("UNAUTHORIZED", exception.getMessage());
    }

    @Test
    void testCreateComicComment_ComicNotFound_ThrowsNotFound() {
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Test comment");

        when(comicRepository.existsById(comicId)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () ->
                commentService.createComicComment(request, userId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        assertEquals("Comic not found", exception.getMessage());
    }

    @Test
    void testCreateComicComment_RootComment_Success() {
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Root comment content");
        request.setParentId(null);

        when(comicRepository.existsById(comicId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        ComicCommentEntity mockSaved = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .content("Root comment content")
                .parentId(null)
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(comicCommentRepository.save(any(ComicCommentEntity.class))).thenReturn(mockSaved);

        ComicCommentDTO result = commentService.createComicComment(request, userId);

        assertNotNull(result);
        assertEquals("Root comment content", result.getContent());
        assertNull(result.getParentId());
        assertEquals(mockSaved.getId(), result.getId());
    }

    @Test
    void testCreateComicComment_ReplyToRootComment_Success() {
        UUID parentId = UUID.randomUUID();
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Reply to root");
        request.setParentId(parentId);

        when(comicRepository.existsById(comicId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        // Mock parent comment (which is root comment, so its parentId is null)
        ComicCommentEntity parentComment = ComicCommentEntity.builder()
                .userId(UUID.randomUUID())
                .comicId(comicId)
                .content("Root comment")
                .parentId(null)
                .build();
        parentComment.setId(parentId);

        when(comicCommentRepository.findById(parentId)).thenReturn(Optional.of(parentComment));

        ComicCommentEntity mockSaved = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .content("Reply to root")
                .parentId(parentId) // Should receive target_comment.id as parentId
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(comicCommentRepository.save(any(ComicCommentEntity.class))).thenReturn(mockSaved);

        ComicCommentDTO result = commentService.createComicComment(request, userId);

        assertNotNull(result);
        assertEquals("Reply to root", result.getContent());
        assertEquals(parentId, result.getParentId());
    }

    @Test
    void testCreateComicComment_ReplyToChildComment_FlattensToLevel2() {
        UUID childCommentId = UUID.randomUUID();
        UUID rootCommentId = UUID.randomUUID();
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Reply to child");
        request.setParentId(childCommentId);

        when(comicRepository.existsById(comicId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        // Mock target parent comment (which is already a child, so its parentId is rootCommentId)
        ComicCommentEntity targetParentComment = ComicCommentEntity.builder()
                .userId(UUID.randomUUID())
                .comicId(comicId)
                .content("Child comment")
                .parentId(rootCommentId)
                .build();
        targetParentComment.setId(childCommentId);

        when(comicCommentRepository.findById(childCommentId)).thenReturn(Optional.of(targetParentComment));

        ComicCommentEntity mockSaved = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .content("Reply to child")
                .parentId(rootCommentId) // Should receive targetParentComment.parentId (rootCommentId) as parentId
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(comicCommentRepository.save(any(ComicCommentEntity.class))).thenReturn(mockSaved);

        ComicCommentDTO result = commentService.createComicComment(request, userId);

        assertNotNull(result);
        assertEquals("Reply to child", result.getContent());
        assertEquals(rootCommentId, result.getParentId());
    }

    @Test
    void testCreateComicComment_ParentDifferentComic_ThrowsBadRequest() {
        UUID parentId = UUID.randomUUID();
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Reply content");
        request.setParentId(parentId);

        when(comicRepository.existsById(comicId)).thenReturn(true);

        // Parent belongs to a different comic
        ComicCommentEntity parentComment = ComicCommentEntity.builder()
                .userId(UUID.randomUUID())
                .comicId(UUID.randomUUID()) // different
                .content("Parent comment")
                .parentId(null)
                .build();
        parentComment.setId(parentId);

        when(comicCommentRepository.findById(parentId)).thenReturn(Optional.of(parentComment));

        CustomException exception = assertThrows(CustomException.class, () ->
                commentService.createComicComment(request, userId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Parent comment belongs to a different comic", exception.getMessage());
    }

    @Test
    void testCreateChapterComment_RootComment_Success() {
        CreateChapterCommentRequest request = new CreateChapterCommentRequest();
        request.setChapterId(chapterId);
        request.setContent("Chapter root comment");
        request.setParentId(null);

        when(chapterRepository.existsById(chapterId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        ChapterCommentEntity mockSaved = ChapterCommentEntity.builder()
                .userId(userId)
                .chapterId(chapterId)
                .content("Chapter root comment")
                .parentId(null)
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(chapterCommentRepository.save(any(ChapterCommentEntity.class))).thenReturn(mockSaved);

        ChapterCommentDTO result = commentService.createChapterComment(request, userId);

        assertNotNull(result);
        assertEquals("Chapter root comment", result.getContent());
        assertNull(result.getParentId());
        assertEquals(mockSaved.getId(), result.getId());
    }

    @Test
    void testGetComicComments_Success() {
        UUID parentId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(comicRepository.existsById(comicId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        ComicCommentEntity c1 = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .content("Comment 1")
                .parentId(parentId)
                .build();
        c1.setId(UUID.randomUUID());

        Page<ComicCommentEntity> mockPage = new PageImpl<>(List.of(c1), pageable, 1);
        when(comicCommentRepository.findByComicIdAndParentId(comicId, parentId, pageable)).thenReturn(mockPage);

        Page<ComicCommentDTO> result = commentService.getComicComments(comicId, parentId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Comment 1", result.getContent().get(0).getContent());
        assertEquals(parentId, result.getContent().get(0).getParentId());
    }

    @Test
    void testGetChapterComments_Success() {
        UUID parentId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(chapterRepository.existsById(chapterId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        ChapterCommentEntity c1 = ChapterCommentEntity.builder()
                .userId(userId)
                .chapterId(chapterId)
                .content("Chapter Comment 1")
                .parentId(parentId)
                .build();
        c1.setId(UUID.randomUUID());

        Page<ChapterCommentEntity> mockPage = new PageImpl<>(List.of(c1), pageable, 1);
        when(chapterCommentRepository.findByChapterIdAndParentId(chapterId, parentId, pageable)).thenReturn(mockPage);

        Page<ChapterCommentDTO> result = commentService.getChapterComments(chapterId, parentId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Chapter Comment 1", result.getContent().get(0).getContent());
        assertEquals(parentId, result.getContent().get(0).getParentId());
    }

    @Test
    void testCreateComicComment_WithMentionId_Success() {
        UUID mentionId = UUID.randomUUID();
        CreateComicCommentRequest request = new CreateComicCommentRequest();
        request.setComicId(comicId);
        request.setContent("Root comment with mention");
        request.setMentionId(mentionId);

        when(comicRepository.existsById(comicId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        UserSnapshot mentionSnapshot = new UserSnapshot();
        mentionSnapshot.setUserId(mentionId);
        mentionSnapshot.setUserName("mentionedUser");
        mentionSnapshot.setAvatarURL("mentionedAvatar");
        when(userService.findUserById(mentionId)).thenReturn(mentionSnapshot);

        ComicCommentEntity mockSaved = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .content("Root comment with mention")
                .mentionId(mentionId)
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(comicCommentRepository.save(any(ComicCommentEntity.class))).thenReturn(mockSaved);

        ComicCommentDTO result = commentService.createComicComment(request, userId);

        assertNotNull(result);
        assertEquals("Root comment with mention", result.getContent());
        assertEquals(mentionId, result.getMentionId());
        assertEquals("mentionedUser", result.getMentionName());
    }

    @Test
    void testCreateChapterComment_WithMentionId_Success() {
        UUID mentionId = UUID.randomUUID();
        CreateChapterCommentRequest request = new CreateChapterCommentRequest();
        request.setChapterId(chapterId);
        request.setContent("Chapter comment with mention");
        request.setMentionId(mentionId);

        when(chapterRepository.existsById(chapterId)).thenReturn(true);
        when(userService.findUserById(userId)).thenReturn(mockUserSnapshot());

        UserSnapshot mentionSnapshot = new UserSnapshot();
        mentionSnapshot.setUserId(mentionId);
        mentionSnapshot.setUserName("mentionedUser");
        mentionSnapshot.setAvatarURL("mentionedAvatar");
        when(userService.findUserById(mentionId)).thenReturn(mentionSnapshot);

        ChapterCommentEntity mockSaved = ChapterCommentEntity.builder()
                .userId(userId)
                .chapterId(chapterId)
                .content("Chapter comment with mention")
                .mentionId(mentionId)
                .build();
        mockSaved.setId(UUID.randomUUID());

        when(chapterCommentRepository.save(any(ChapterCommentEntity.class))).thenReturn(mockSaved);

        ChapterCommentDTO result = commentService.createChapterComment(request, userId);

        assertNotNull(result);
        assertEquals("Chapter comment with mention", result.getContent());
        assertEquals(mentionId, result.getMentionId());
        assertEquals("mentionedUser", result.getMentionName());
    }
}
