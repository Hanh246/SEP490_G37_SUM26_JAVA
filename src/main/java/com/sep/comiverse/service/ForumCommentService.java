package com.sep.comiverse.service;

import com.sep.comiverse.dto.ForumCommentDTO;
import com.sep.comiverse.dto.request.CreateForumCommentRequest;
import com.sep.comiverse.dto.request.UpdateForumCommentRequest;
import com.sep.comiverse.entity.ForumCommentEntity;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumCommentRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.ProfanityFilterUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForumCommentService {

    private final IForumCommentRepository forumCommentRepository;
    private final IForumThreadRepository forumThreadRepository;
    private final IUserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ForumCommentDTO> getComments(UUID threadId) {
        requireThread(threadId);
        return forumCommentRepository.findByThreadIdAndDeletedFalseOrderByCreatedAtAsc(threadId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ForumCommentDTO createComment(UUID threadId, CreateForumCommentRequest request, UUID actorId) {
        if (actorId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        if (ProfanityFilterUtil.containsProfanity(request.getContent())) {
            throw new CustomException(400, "Your comment contains inappropriate language.", HttpStatus.BAD_REQUEST);
        }

        ForumThreadEntity thread = requireThread(threadId);
        if (Boolean.TRUE.equals(thread.getIsLocked())) {
            throw new CustomException(409, "This discussion thread is locked", HttpStatus.CONFLICT);
        }

        UserEntity actor = userRepository.findByIdWithRole(actorId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        ForumCommentEntity parent = null;
        if (request.getParentId() != null) {
            parent = forumCommentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(400, "Parent comment not found", HttpStatus.BAD_REQUEST));
            if (!threadId.equals(parent.getThreadId())) {
                throw new CustomException(400, "Parent comment belongs to a different thread", HttpStatus.BAD_REQUEST);
            }
        }

        ForumCommentEntity saved = forumCommentRepository.save(ForumCommentEntity.builder()
                .threadId(threadId)
                .userId(actorId)
                .content(request.getContent().trim())
                .parentId(parent == null ? null : parent.getId())
                .build());

        thread.setReplies((thread.getReplies() == null ? 0 : thread.getReplies()) + 1);
        forumThreadRepository.save(thread);

        UUID recipientId = resolveRecipientId(thread, parent);
        if (recipientId != null && !recipientId.equals(actorId)) {
            String actorName = displayName(actor);
            String notificationTitle = parent == null
                    ? "New reply to your forum post"
                    : "New reply to your forum comment";
            String message = actorName + " replied in \"" + abbreviate(thread.getTitle(), 80) + "\".";
            String actionUrl = "/forum/thread/" + threadId + "?comment=" + saved.getId();
            notificationService.notifyUser(
                    recipientId,
                    notificationTitle,
                    message,
                    "FORUM",
                    actionUrl,
                    NotificationPreferenceKey.FORUM_ACTIVITY
            );
        }

        return toDto(saved, actor);
    }

    @Transactional
    public ForumCommentDTO updateComment(UUID commentId, UpdateForumCommentRequest request, UUID actorId) {
        if (actorId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        if (ProfanityFilterUtil.containsProfanity(request.getContent())) {
            throw new CustomException(400, "Your comment contains inappropriate language.", HttpStatus.BAD_REQUEST);
        }

        ForumCommentEntity comment = forumCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Comment not found", HttpStatus.NOT_FOUND);
        }

        if (!comment.getUserId().equals(actorId)) {
            throw new CustomException(403, "You do not have permission to edit this comment", HttpStatus.FORBIDDEN);
        }

        comment.setContent(request.getContent().trim());
        ForumCommentEntity saved = forumCommentRepository.save(comment);

        return toDto(saved);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID actorId, String role) {
        if (actorId == null) {
            throw new CustomException(401, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        ForumCommentEntity comment = forumCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(404, "Comment not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new CustomException(404, "Comment not found", HttpStatus.NOT_FOUND);
        }

        boolean isOwner = comment.getUserId().equals(actorId);
        boolean isAdminOrModerator = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);

        if (!isOwner && !isAdminOrModerator) {
            throw new CustomException(403, "You do not have permission to delete this comment", HttpStatus.FORBIDDEN);
        }

        comment.setDeleted(true);
        forumCommentRepository.save(comment);
    }

    private ForumThreadEntity requireThread(UUID threadId) {
        return forumThreadRepository.findById(threadId)
                .orElseThrow(() -> new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND));
    }

    private ForumCommentDTO toDto(ForumCommentEntity comment) {
        UserEntity author = userRepository.findByIdWithRole(comment.getUserId()).orElse(null);
        return toDto(comment, author);
    }

    private ForumCommentDTO toDto(ForumCommentEntity comment, UserEntity author) {
        return ForumCommentDTO.builder()
                .id(comment.getId())
                .threadId(comment.getThreadId())
                .userId(comment.getUserId())
                .author(author == null ? "Deleted user" : displayName(author))
                .avatarUrl(author == null ? null : author.getAvatarUrl())
                .content(comment.getContent())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private UUID resolveRecipientId(ForumThreadEntity thread, ForumCommentEntity parent) {
        if (parent != null) {
            return parent.getUserId();
        }
        if (thread.getAuthorId() != null) {
            return thread.getAuthorId();
        }

        String legacyAuthor = thread.getAuthor();
        if (legacyAuthor == null || legacyAuthor.isBlank()) {
            return null;
        }

        UserEntity owner = userRepository.findByUsername(legacyAuthor.trim()).orElse(null);
        if (owner == null) {
            List<UserEntity> matches = userRepository.findByUsernameOrFullNameIgnoreCase(legacyAuthor.trim());
            if (matches.size() == 1) {
                owner = matches.get(0);
            }
        }
        if (owner == null) {
            return null;
        }

        thread.setAuthorId(owner.getId());
        return owner.getId();
    }

    private String displayName(UserEntity user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }
        return user.getUsername() == null || user.getUsername().isBlank()
                ? "ComiVerse user"
                : user.getUsername().trim();
    }

    private String abbreviate(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? "a discussion" : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength - 3) + "...";
    }
}
