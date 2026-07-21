package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ForumThreadDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ForumThreadCrudPlugin;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
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
        dto.setReplies(0);
        dto.setViews(0);
        dto.setIsPinned(false);
        dto.setIsLocked(false);
        dto.setIsReported(false);
        dto.setReportReason(null);
        return super.create(dto);
    }

    @GetMapping("/all")
    public ResponseEntity<BaseResponse<List<ForumThreadDTO>>> listAll() {
        return ResponseEntity.ok(BaseResponse.<List<ForumThreadDTO>>builder()
                .success(true)
                .data(crudPlugin.listAll())
                .build());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ForumThreadDTO>> update(@PathVariable UUID id, @RequestBody ForumThreadDTO dto) {
        ForumThreadDTO existing = crudPlugin.read(id)
                .orElseThrow(() -> new CustomException(404, "Discussion thread not found", HttpStatus.NOT_FOUND));
        dto.setAuthorId(existing.getAuthorId());
        dto.setAuthor(existing.getAuthor());
        ForumThreadDTO updated = crudPlugin.update(id, dto);
        
        if (dto.getIsReported() != null && !dto.getIsReported()) {
            auditLogService.log("FORUM_MODERATION", "Resolved forum report for thread: \"" + updated.getTitle() + "\"");
        } else {
            auditLogService.log("FORUM_MODERATION", "Updated forum thread category/details: \"" + updated.getTitle() + "\"");
        }
        
        return ResponseEntity.ok(BaseResponse.<ForumThreadDTO>builder()
                .success(true)
                .data(updated)
                .build());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        String threadTitle = "Unknown Thread";
        try {
            var opt = crudPlugin.read(id);
            if (opt.isPresent()) {
                threadTitle = opt.get().getTitle();
            }
        } catch (Exception e) {}
        
        ResponseEntity<BaseResponse<Void>> response = super.delete(id);
        auditLogService.log("FORUM_MODERATION", "Deleted forum thread: \"" + threadTitle + "\"");
        return response;
    }
}
