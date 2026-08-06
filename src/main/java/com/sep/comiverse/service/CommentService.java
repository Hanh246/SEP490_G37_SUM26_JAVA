package com.sep.comiverse.service;

import com.sep.comiverse.dto.ChapterCommentDTO;
import com.sep.comiverse.dto.ComicCommentDTO;
import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.dto.request.CreateChapterCommentRequest;
import com.sep.comiverse.dto.request.CreateComicCommentRequest;
import com.sep.comiverse.entity.ChapterCommentEntity;
import com.sep.comiverse.entity.ComicCommentEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterCommentRepository;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicCommentRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.util.ProfanityFilterUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final IComicCommentRepository comicCommentRepository;
    private final IChapterCommentRepository chapterCommentRepository;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    @Transactional
    public ComicCommentDTO createComicComment(CreateComicCommentRequest request, UUID userId) {
        // 1. Verify user is authenticated
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        if (ProfanityFilterUtil.containsProfanity(request.getContent())) {
            throw new CustomException(400, "Your comment contains inappropriate language.", HttpStatus.BAD_REQUEST);
        }

        // 2. Verify Comic exists
        if (!comicRepository.existsById(request.getComicId())) {
            throw new CustomException(404, "Comic not found", HttpStatus.NOT_FOUND);
        }

        ComicCommentEntity parentComment = null;
        UUID finalParentId = null;
        UUID reqParentId = request.getParentId();

        // 3. Process 2-level comment nesting logic
        if (reqParentId != null) {
            parentComment = comicCommentRepository.findById(reqParentId)
                    .orElseThrow(() -> new CustomException(400, "Parent comment not found", HttpStatus.BAD_REQUEST));

            // Verify the parent comment is on the same comic
            if (!parentComment.getComicId().equals(request.getComicId())) {
                throw new CustomException(400, "Parent comment belongs to a different comic", HttpStatus.BAD_REQUEST);
            }

            if (parentComment.getParentId() == null) {
                // Case 1: Parent comment is a root comment (parentId is null)
                // -> Reply comment gets the parent comment's ID as its parentId
                finalParentId = parentComment.getId();
            } else {
                // Case 2: Parent comment is already a sub-comment (parentId is not null, let's say X)
                // -> Reply comment also gets X as its parentId (flattened to level 2)
                finalParentId = parentComment.getParentId();
            }
        }

        ComicCommentEntity newComment = ComicCommentEntity.builder()
                .userId(userId)
                .comicId(request.getComicId())
                .content(request.getContent())
                .parentId(finalParentId)
                .mentionId(request.getMentionId())
                .build();

        ComicCommentEntity saved = comicCommentRepository.save(newComment);

        sendReplyNotification(
                userId,
                request.getMentionId(),
                parentComment != null ? parentComment.getUserId() : null,
                "/comic/" + request.getComicId() + "?comment=" + saved.getId()
        );

        return mapToComicCommentDTO(saved);
    }

    @Transactional
    public ChapterCommentDTO createChapterComment(CreateChapterCommentRequest request, UUID userId) {
        // 1. Verify user is authenticated
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        if (ProfanityFilterUtil.containsProfanity(request.getContent())) {
            throw new CustomException(400, "Your comment contains inappropriate language.", HttpStatus.BAD_REQUEST);
        }

        // 2. Verify Chapter exists and retain its comic for the mobile deep link.
        UUID comicId = chapterRepository.findComicIdByChapterId(request.getChapterId())
                .orElseThrow(() -> new CustomException(404, "Chapter not found", HttpStatus.NOT_FOUND));

        ChapterCommentEntity parentComment = null;
        UUID finalParentId = null;
        UUID reqParentId = request.getParentId();

        // 3. Process 2-level comment nesting logic
        if (reqParentId != null) {
            parentComment = chapterCommentRepository.findById(reqParentId)
                    .orElseThrow(() -> new CustomException(400, "Parent comment not found", HttpStatus.BAD_REQUEST));

            // Verify the parent comment is on the same chapter
            if (!parentComment.getChapterId().equals(request.getChapterId())) {
                throw new CustomException(400, "Parent comment belongs to a different chapter", HttpStatus.BAD_REQUEST);
            }

            if (parentComment.getParentId() == null) {
                // Case 1: Parent comment is a root comment (parentId is null)
                // -> Reply comment gets the parent comment's ID as its parentId
                finalParentId = parentComment.getId();
            } else {
                // Case 2: Parent comment is already a sub-comment (parentId is not null, let's say X)
                // -> Reply comment also gets X as its parentId (flattened to level 2)
                finalParentId = parentComment.getParentId();
            }
        }

        ChapterCommentEntity newComment = ChapterCommentEntity.builder()
                .userId(userId)
                .chapterId(request.getChapterId())
                .content(request.getContent())
                .parentId(finalParentId)
                .mentionId(request.getMentionId())
                .build();

        ChapterCommentEntity saved = chapterCommentRepository.save(newComment);

        sendReplyNotification(
                userId,
                request.getMentionId(),
                parentComment != null ? parentComment.getUserId() : null,
                "/comic/" + comicId + "/chapter/" + request.getChapterId()
                        + "?comment=" + saved.getId()
        );

        return mapToChapterCommentDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<ComicCommentDTO> getComicComments(UUID comicId, UUID parentId, Pageable pageable) {
        if (!comicRepository.existsById(comicId)) {
            throw new CustomException(404, "Comic not found", HttpStatus.NOT_FOUND);
        }

        Sort sort = (parentId != null)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageableWithSort = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        return comicCommentRepository.findByComicIdAndParentId(comicId, parentId, pageableWithSort)
                .map(this::mapToComicCommentDTO);
    }

    @Transactional(readOnly = true)
    public Page<ChapterCommentDTO> getChapterComments(UUID chapterId, UUID parentId, Pageable pageable) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new CustomException(404, "Chapter not found", HttpStatus.NOT_FOUND);
        }

        Sort sort = (parentId != null)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageableWithSort = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        return chapterCommentRepository.findByChapterIdAndParentId(chapterId, parentId, pageableWithSort)
                .map(this::mapToChapterCommentDTO);
    }

    @Transactional(readOnly = true)
    public List<ComicCommentDTO> getComicCommentThreadById(UUID commentId) {
        ComicCommentEntity comment = comicCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Comic comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Comic comment not found or has been deleted", HttpStatus.NOT_FOUND);
        }

        if (comment.getParentId() == null) {
            return List.of(mapToComicCommentDTO(comment));
        }

        ComicCommentEntity rootComment = comicCommentRepository.findById(comment.getParentId())
                .orElse(null);

        if (rootComment == null || Boolean.TRUE.equals(rootComment.getDeleted())) {
            throw new CustomException(404, "Parent comment has been deleted", HttpStatus.NOT_FOUND);
        }

        List<ComicCommentDTO> result = new ArrayList<>();
        result.add(mapToComicCommentDTO(rootComment));
        result.add(mapToComicCommentDTO(comment));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChapterCommentDTO> getChapterCommentThreadById(UUID commentId) {
        ChapterCommentEntity comment = chapterCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Chapter comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Chapter comment not found or has been deleted", HttpStatus.NOT_FOUND);
        }

        if (comment.getParentId() == null) {
            return List.of(mapToChapterCommentDTO(comment));
        }

        ChapterCommentEntity rootComment = chapterCommentRepository.findById(comment.getParentId())
                .orElse(null);

        if (rootComment == null || Boolean.TRUE.equals(rootComment.getDeleted())) {
            throw new CustomException(404, "Parent comment has been deleted", HttpStatus.NOT_FOUND);
        }

        List<ChapterCommentDTO> result = new ArrayList<>();
        result.add(mapToChapterCommentDTO(rootComment));
        result.add(mapToChapterCommentDTO(comment));
        return result;
    }

    @Transactional
    public void deleteComicComment(UUID commentId, UUID userId, String userRole) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        ComicCommentEntity comment = comicCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Comic comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Comic comment not found", HttpStatus.NOT_FOUND);
        }

        boolean isOwner = comment.getUserId().equals(userId);
        boolean isAdminOrModerator = "ADMIN".equalsIgnoreCase(userRole) || "MODERATOR".equalsIgnoreCase(userRole);

        if (!isOwner && !isAdminOrModerator) {
            throw new CustomException(403, "You do not have permission to delete this comment", HttpStatus.FORBIDDEN);
        }

        comment.setDeleted(true);
        comicCommentRepository.save(comment);

        if (comment.getParentId() == null) {
            comicCommentRepository.softDeleteByParentId(comment.getId());
        }
    }

    @Transactional
    public void deleteChapterComment(UUID commentId, UUID userId, String userRole) {
        if (userId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        ChapterCommentEntity comment = chapterCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Chapter comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Chapter comment not found", HttpStatus.NOT_FOUND);
        }

        boolean isOwner = comment.getUserId().equals(userId);
        boolean isAdminOrModerator = "ADMIN".equalsIgnoreCase(userRole) || "MODERATOR".equalsIgnoreCase(userRole);

        if (!isOwner && !isAdminOrModerator) {
            throw new CustomException(403, "You do not have permission to delete this comment", HttpStatus.FORBIDDEN);
        }

        comment.setDeleted(true);
        chapterCommentRepository.save(comment);

        if (comment.getParentId() == null) {
            chapterCommentRepository.softDeleteByParentId(comment.getId());
        }
    }

    private ComicCommentDTO mapToComicCommentDTO(ComicCommentEntity entity) {
        UserSnapshot userSnapshot = getUserById(entity.getUserId());
        UserSnapshot mentionSnapShot = getUserById(entity.getMentionId());
        return ComicCommentDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userName(userSnapshot != null ? userSnapshot.getUserName() : null)
                .userAvatar(userSnapshot != null ? userSnapshot.getAvatarURL() : null)
                .comicId(entity.getComicId())
                .content(entity.getContent())
                .parentId(entity.getParentId())
                .mentionId(entity.getMentionId())
                .mentionName(mentionSnapShot != null ? mentionSnapShot.getUserName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ChapterCommentDTO mapToChapterCommentDTO(ChapterCommentEntity entity) {
        UserSnapshot userSnapshot = getUserById(entity.getUserId());
        UserSnapshot mentionSnapShot = getUserById(entity.getMentionId());
        return ChapterCommentDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userName(userSnapshot != null ? userSnapshot.getUserName() : null)
                .userAvatar(userSnapshot != null ? userSnapshot.getAvatarURL() : null)
                .chapterId(entity.getChapterId())
                .content(entity.getContent())
                .parentId(entity.getParentId())
                .mentionId(entity.getMentionId())
                .mentionName(mentionSnapShot != null ? mentionSnapShot.getUserName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UserSnapshot getUserById(UUID userId){
        if (userId == null) return null;
        return userService.findUserById(userId);
    }

    private void sendReplyNotification(UUID actorId, UUID mentionId, UUID parentAuthorId, String actionUrl) {
        UUID recipientId = mentionId != null ? mentionId : parentAuthorId;

        if (recipientId != null && !recipientId.equals(actorId)) {
            UserSnapshot actorSnapshot = getUserById(actorId);
            String actorName = (actorSnapshot != null && actorSnapshot.getUserName() != null)
                    ? actorSnapshot.getUserName()
                    : "Someone";
            String notificationTitle = "New reply to your comment";
            String message = actorName + " replied to your comment.";
            notificationService.notifyUser(
                    recipientId,
                    notificationTitle,
                    message,
                    "COMMENT",
                    actionUrl,
                    NotificationPreferenceKey.COMMENT_REPLIES
            );
        }
    }
}
