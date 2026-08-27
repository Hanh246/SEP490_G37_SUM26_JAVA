package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ReportForumThreadRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ForumThreadFollowResponse;
import com.sep.comiverse.dto.response.ForumThreadLikeResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ForumThreadCrudPlugin;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.ForumThreadFollowService;
import com.sep.comiverse.service.ForumThreadLikeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/forum-threads")
public class ForumThreadController extends BaseController<ForumThreadEntity, ForumThreadDTO, UUID, PaginationSearchDTO> {

    @Autowired
    private com.sep.comiverse.service.AuditLogService auditLogService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private com.sep.comiverse.repository.IForumCategoryRepository forumCategoryRepository;

    @Autowired
    private com.sep.comiverse.repository.IForumThreadRepository forumThreadRepository;

    @Autowired
    private ForumThreadLikeService forumThreadLikeService;

    @Autowired
    private ForumThreadFollowService forumThreadFollowService;

    @Autowired
    public ForumThreadController(ForumThreadCrudPlugin crud) {
        super(crud, ForumThreadEntity.class);
    }

    @Override
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ForumThreadDTO>> create(@Valid @RequestBody ForumThreadDTO dto) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        var user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        String displayName = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName().trim();
        dto.setAuthorId(userId);
        dto.setAuthor(displayName);
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setReplies(0);
        dto.setViews(0);
        dto.setLikes(0);
        dto.setIsLikedByCurrentUser(false);
        dto.setIsFollowedByCurrentUser(false);
        dto.setIsPinned(false);
        dto.setIsLocked(false);
        dto.setIsReported(false);
        dto.setReportReason(null);
        String category = dto.getCategory() == null || dto.getCategory().isBlank()
                ? "General"
                : dto.getCategory().trim();
        if (!forumCategoryRepository.existsByNameIgnoreCaseAndDeletedFalse(category)) {
            throw new CustomException(400, "Forum category does not exist", HttpStatus.BAD_REQUEST);
        }
        dto.setCategory(category);
        return super.create(dto);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ForumThreadDTO>>> listAll() {
        List<ForumThreadDTO> threads = decorateEngagementState(crudPlugin.listAll());
        return ResponseEntity.ok(BaseResponse.<List<ForumThreadDTO>>builder()
                .success(true)
                .data(threads)
                .build());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ForumThreadDTO>> findById(@PathVariable UUID id) {
        return crudPlugin.read(id)
                .map(dto -> ResponseEntity.<BaseResponse<ForumThreadDTO>>ok(BaseResponse.<ForumThreadDTO>builder()
                        .success(true)
                        .data(decorateEngagementState(dto))
                        .build()))
                .orElseGet(() -> ResponseEntity.<BaseResponse<ForumThreadDTO>>status(HttpStatus.NOT_FOUND)
                        .body(BaseResponse.<ForumThreadDTO>builder().success(false).build()));
    }

    @Override
    @GetMapping
    public ResponseEntity<PaginationResponse<List<ForumThreadDTO>>> findAll(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO
    ) {
        var page = crudPlugin.list(paginationDTO);
        List<ForumThreadDTO> threads = decorateEngagementState(page.toList());
        return ResponseEntity.ok(PaginationResponse.<List<ForumThreadDTO>>builder()
                .metadata(new PaginationMetadata(
                        paginationDTO.getPage(),
                        paginationDTO.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                ))
                .success(true)
                .data(threads)
                .build());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ForumThreadDTO>> update(@PathVariable UUID id, @RequestBody ForumThreadDTO dto) {
        ForumThreadDTO existing = crudPlugin.read(id)
                .orElseThrow(() -> new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND));
        dto.setAuthorId(existing.getAuthorId());
        dto.setAuthor(existing.getAuthor());
        dto.setLikes(existing.getLikes());
        dto.setIsLikedByCurrentUser(existing.getIsLikedByCurrentUser());
        dto.setIsFollowedByCurrentUser(existing.getIsFollowedByCurrentUser());
        ForumThreadDTO updated = crudPlugin.update(id, dto);
        
        if (dto.getIsReported() != null && !dto.getIsReported()) {
            auditLogService.log("FORUM_MODERATION", "Resolved forum report for thread: \"" + updated.getTitle() + "\"");
        } else {
            auditLogService.log("FORUM_MODERATION", "Updated forum thread category/details: \"" + updated.getTitle() + "\"");
        }
        
        return ResponseEntity.ok(BaseResponse.<ForumThreadDTO>builder()
                .success(true)
                .data(decorateEngagementState(updated))
                .build());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        String threadTitle = "Unknown Thread";
        ForumThreadDTO existing = crudPlugin.read(id)
                .orElseThrow(() -> new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND));
        threadTitle = existing.getTitle();
        UUID currentUserId = jwtTokenUtil.getCurrentUserId();
        var currentUser = userRepository.findByIdWithRole(currentUserId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        String role = currentUser.getRole() == null ? "" : currentUser.getRole().getRoleName();
        boolean moderator = "MODERATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        boolean owner = currentUserId.equals(existing.getAuthorId());
        if (!moderator && !owner) {
            throw new CustomException(403, "You do not have permission to delete this thread", HttpStatus.FORBIDDEN);
        }
        
        ResponseEntity<BaseResponse<Void>> response = super.delete(id);
        auditLogService.log("FORUM_MODERATION", "Deleted forum thread: \"" + threadTitle + "\"");
        return response;
    }

    @PostMapping("/{id}/view")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<Void>> incrementView(@PathVariable UUID id) {
        if (forumThreadRepository.incrementViews(id) == 0) {
            throw new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(BaseResponse.<Void>builder().success(true).build());
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ForumThreadLikeResponse>> toggleLike(@PathVariable UUID id) {
        ForumThreadLikeResponse result = forumThreadLikeService.toggle(id, jwtTokenUtil.getCurrentUserId());
        return ResponseEntity.ok(BaseResponse.<ForumThreadLikeResponse>builder()
                .success(true)
                .data(result)
                .message(result.isLiked() ? "Thread liked" : "Thread unliked")
                .build());
    }

    @PostMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<ForumThreadFollowResponse>> toggleFollow(@PathVariable UUID id) {
        ForumThreadFollowResponse result = forumThreadFollowService.toggle(id, jwtTokenUtil.getCurrentUserId());
        return ResponseEntity.ok(BaseResponse.<ForumThreadFollowResponse>builder()
                .success(true)
                .data(result)
                .message(result.isFollowing() ? "Thread followed" : "Thread unfollowed")
                .build());
    }

    @PostMapping("/{id}/report")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<Void>> reportThread(
            @PathVariable UUID id,
            @Valid @RequestBody ReportForumThreadRequest request
    ) {
        ForumThreadEntity thread = forumThreadRepository.findById(id)
                .orElseThrow(() -> new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND));
        thread.setIsReported(true);
        thread.setReportReason(request.getReason().trim());
        forumThreadRepository.save(thread);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Forum thread reported successfully")
                .build());
    }

    private List<ForumThreadDTO> decorateEngagementState(List<ForumThreadDTO> threads) {
        UUID currentUserId = resolveCurrentUserId();
        Set<UUID> likedIds = forumThreadLikeService.findLikedThreadIds(currentUserId);
        Set<UUID> followedIds = forumThreadFollowService.findFollowedThreadIds(currentUserId);
        applyAuthorAvatars(threads);
        threads.forEach(thread -> applyEngagementState(thread, likedIds, followedIds));
        return threads;
    }

    private ForumThreadDTO decorateEngagementState(ForumThreadDTO thread) {
        UUID currentUserId = resolveCurrentUserId();
        Set<UUID> likedIds = forumThreadLikeService.findLikedThreadIds(currentUserId);
        Set<UUID> followedIds = forumThreadFollowService.findFollowedThreadIds(currentUserId);
        applyAuthorAvatars(List.of(thread));
        applyEngagementState(thread, likedIds, followedIds);
        return thread;
    }

    private void applyAuthorAvatars(List<ForumThreadDTO> threads) {
        Set<UUID> authorIds = threads.stream()
                .map(ForumThreadDTO::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (authorIds.isEmpty()) {
            return;
        }

        Map<UUID, String> avatarsByAuthorId = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(user -> {
            if (!Boolean.TRUE.equals(user.getDeleted())) {
                avatarsByAuthorId.put(user.getId(), user.getAvatarUrl());
            }
        });
        threads.forEach(thread -> thread.setAvatarUrl(avatarsByAuthorId.get(thread.getAuthorId())));
    }

    private UUID resolveCurrentUserId() {
        try {
            return jwtTokenUtil.getCurrentUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyEngagementState(ForumThreadDTO thread, Set<UUID> likedIds, Set<UUID> followedIds) {
        thread.setLikes(Math.max(0, thread.getLikes() == null ? 0 : thread.getLikes()));
        thread.setIsLikedByCurrentUser(thread.getId() != null && likedIds.contains(thread.getId()));
        thread.setIsFollowedByCurrentUser(thread.getId() != null && followedIds.contains(thread.getId()));
    }
}
