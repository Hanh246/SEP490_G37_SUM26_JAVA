package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterDTO;
import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/chapters")
public class ChapterController {

    private static final Set<String> INCLUDE_UNPUBLISHED_ROLES = Set.of(
            "ADMIN", "MODERATOR", "AUTHOR", "TRANSLATOR", "PROJECT_LEADER"
    );

    private final ChapterCrudPlugin chapterCrudPlugin;
    private final JwtTokenUtil jwtTokenUtil;
    
    @org.springframework.beans.factory.annotation.Autowired
    private IChapterRepository chapterRepository;
    
    @org.springframework.beans.factory.annotation.Autowired
    private IComicRepository comicRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.sep.comiverse.repository.ISubmissionRepository submissionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.sep.comiverse.repository.IProjectTeamRepository projectTeamRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.sep.comiverse.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.sep.comiverse.service.AuthorComicService authorComicService;

    public ChapterController(ChapterCrudPlugin chapterCrudPlugin, JwtTokenUtil jwtTokenUtil) {
        this.chapterCrudPlugin = chapterCrudPlugin;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * ADMIN CRUD - tạo chapter trực tiếp.
     * Bình thường author nên upload qua AuthorChapterController,
     * không nên cho user thường gọi endpoint này.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> create(@Valid @RequestBody ChapterDTO dto) {
        ChapterDTO created = chapterCrudPlugin.create(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ChapterDTO>builder()
                        .success(true)
                        .data(created)
                        .build());
    }

    /**
     * ADMIN CRUD - đọc chapter bất kỳ theo id.
     * Endpoint này dành cho admin, không dùng cho reader public.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> findById(@PathVariable UUID id) {
        return chapterCrudPlugin.read(id)
                .<ResponseEntity<BaseResponse<ChapterDTO>>>map(dto -> ResponseEntity.ok(
                        BaseResponse.<ChapterDTO>builder()
                                .success(true)
                                .data(dto)
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BaseResponse.<ChapterDTO>builder()
                                .success(false)
                                .build()));
    }

    /**
     * ADMIN CRUD - danh sách tất cả chapter.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<PaginationResponse<List<ChapterDTO>>> findAll(
            @Valid @ParameterObject PaginationSearchDTO paginationDTO
    ) {
        PaginationSearchDTO safePagination =
                paginationDTO != null ? paginationDTO : new PaginationSearchDTO();

        Page<ChapterDTO> data = chapterCrudPlugin.list(safePagination);

        return ResponseEntity.ok(PaginationResponse.<List<ChapterDTO>>builder()
                .success(true)
                .metadata(new PaginationMetadata(
                        safePagination.getPage(),
                        safePagination.getSize(),
                        data.getTotalElements(),
                        data.getTotalPages()
                ))
                .data(data.toList())
                .build());
    }

    /**
     * ADMIN CRUD - sửa chapter trực tiếp.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BaseResponse<ChapterDTO>> update(
            @PathVariable UUID id,
            @RequestBody ChapterDTO dto
    ) {
        ChapterDTO updated = chapterCrudPlugin.update(id, dto);

        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                .success(true)
                .data(updated)
                .build());
    }

    /**
     * ADMIN CRUD - xóa chapter trực tiếp.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        chapterCrudPlugin.delete(id);

        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .build());
    }

    /**
     * Reader/Frontend đọc nội dung chapter.
     * Quan trọng: trong getChapterDetail phải lọc chapter moderationStatus = PUBLISHED
     * nếu user không phải ADMIN/MODERATOR/owner author.
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "Get chapter detail")
    public ResponseEntity<BaseResponse<ChapterDTO>> getChapterDetail(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        String clientIp = request.getRemoteAddr();

        return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                .success(true)
                .data(chapterCrudPlugin.getChapterDetail(id, userId, clientIp))
                .build());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ChapterDTO>> approveChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        try {
            UUID modId = principal != null ? principal.getId() : null;

            ChapterEntity chapter = chapterRepository.findById(id)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chapter with id " + id + " not found"));

            chapter.setModerationStatus(ChapterStatus.PUBLISHED);
            chapter.setApprovedById(modId);
            chapter.setApprovedAt(java.time.Instant.now());
            chapter.setRejectionReason(null);
            chapter.setRejectedById(null);

            ChapterEntity savedChapter = chapterRepository.save(chapter);

            // ── CRITICAL: Sync submissions table to prevent data inconsistency ──
            // When a chapter is approved directly (not via /submissions/{id}/approve),
            // we must also update any pending submission records for this chapter.
            // Without this, the Review Queue (which queries submissions table) will still
            // show these chapters as PENDING even though they are PUBLISHED in chapters table.
            try {
                java.util.List<com.sep.comiverse.entity.SubmissionEntity> pendingSubmissions =
                        submissionRepository.findAllByChapterIdAndDeletedFalse(id);
                UUID moderatorId = principal != null ? principal.getId() : null;
                for (com.sep.comiverse.entity.SubmissionEntity sub : pendingSubmissions) {
                    // Preserve moderation history: only the currently pending review
                    // may become approved. Historical REJECTED/CANCELLED rows stay immutable.
                    if ("pending".equalsIgnoreCase(sub.getStatus())) {
                        sub.setStatus("approved");
                        if (moderatorId != null) {
                            sub.setModeratorId(moderatorId);
                        }
                        submissionRepository.save(sub);
                    }
                }
            } catch (Exception subEx) {
                // Log but don't fail the chapter approval if submission sync has issues
                System.err.println("[ChapterController.approveChapter] Warning: Failed to sync submissions table for chapter " + id + ": " + subEx.getMessage());
            }

            ComicEntity comic = null;
            if (savedChapter.getComic() != null) {
                comic = comicRepository.findById(savedChapter.getComic().getId()).orElse(null);
            }
            if (comic != null) {
                if (comic.getModerationStatus() != ComicModerationStatus.PUBLISHED) {
                    authorComicService.assertPublishedComicQuotaAvailable(comic);
                    comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                    comic.setApprovedById(modId);
                    comic.setApprovedAt(java.time.Instant.now());
                }

                comic.setLatestChapterNumber(savedChapter.getChapterNumber());
                comic.setLastChapterUpdatedAt(java.time.Instant.now());

                long publishedChapterCount = chapterRepository.countByComic_IdAndModerationStatusAndDeletedFalse(
                        comic.getId(),
                        ChapterStatus.PUBLISHED
                );
                comic.setChapterCount(publishedChapterCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) publishedChapterCount);
                
                comicRepository.save(comic);
                chapterCrudPlugin.evictChaptersCache(comic.getId());
                
                notifyTeamOfNewChapters(comic, savedChapter.getTitle());
            }

            ChapterDTO responseDto = null;
            try {
                responseDto = chapterCrudPlugin.getPlugin().toDto(savedChapter);
            } catch (Exception e) {
                // Fallback if ModelMapper fails
                responseDto = new ChapterDTO();
                responseDto.setId(savedChapter.getId());
                responseDto.setChapterNumber(savedChapter.getChapterNumber());
                if (savedChapter.getModerationStatus() != null) {
                    responseDto.setModerationStatus(savedChapter.getModerationStatus());
                }
                if (comic != null) responseDto.setComicId(comic.getId());
            }

            return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                    .success(true)
                    .data(responseDto)
                    .build());
        } catch (CustomException ex) {
            // Preserve quota/business HTTP status and let @Transactional roll back
            // chapter/submission updates already made in this request.
            throw ex;
        } catch (Exception ex) {
            java.io.StringWriter sw = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(sw));
            return ResponseEntity.status(500).body(BaseResponse.<ChapterDTO>builder()
                    .success(false)
                    .message("FATAL ERROR: " + ex.getMessage() + " | TRACE: " + sw.toString())
                    .build());
        }
    }

    @PostMapping("/{id}/takedown")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BaseResponse<ChapterDTO>> takedownChapter(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        try {
            String reason = body.getOrDefault("reason", "No reason provided");
            ChapterEntity chapter = chapterRepository.findById(id)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Chapter with id " + id + " not found"));
            
            // Set status to REJECTED so the author has to resubmit
            chapter.setModerationStatus(ChapterStatus.REJECTED);
            chapter.setRejectionReason(reason);
            if (principal != null) {
                chapter.setRejectedById(principal.getId());
            }
            ChapterEntity savedChapter = chapterRepository.save(chapter);

            // Notify the author
            if (chapter.getComic() != null && chapter.getComic().getAuthorId() != null) {
                notificationService.notifyUser(
                        chapter.getComic().getAuthorId(),
                        "Chapter Taken Down",
                        "Your chapter '" + chapter.getTitle() + "' in comic '" + chapter.getComic().getTitle() + "' was taken down. Reason: " + reason,
                        "MODERATION",
                        com.sep.comiverse.entity.enums.NotificationPreferenceKey.SUBMISSION_STATUS
                );
            }
            
            chapterCrudPlugin.evictChaptersCache(chapter.getComic() != null ? chapter.getComic().getId() : null);

            ChapterDTO responseDto = chapterCrudPlugin.getPlugin().toDto(savedChapter);
            return ResponseEntity.ok(BaseResponse.<ChapterDTO>builder()
                    .success(true)
                    .data(responseDto)
                    .build());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(BaseResponse.<ChapterDTO>builder()
                    .success(false)
                    .message("Error: " + ex.getMessage())
                    .build());
        }
    }

    /**
     * Reader/Frontend lấy danh sách chapter của comic.
     * Quan trọng: trong getChaptersByComicId phải chỉ trả chapter PUBLISHED.
     */
    @GetMapping("/comic/{comicId}")
    @Operation(summary = "Get list of chapters by comic ID")
    public ResponseEntity<BaseResponse<List<ChapterLiteDTO>>> getChaptersByComicId(
            @PathVariable UUID comicId,
            @RequestParam(value = "includeAll", required = false, defaultValue = "false") boolean includeAll,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String role = principal == null || principal.getRole() == null
                ? ""
                : principal.getRole().trim().toUpperCase(Locale.ROOT);
        boolean includeUnpublished = includeAll && INCLUDE_UNPUBLISHED_ROLES.contains(role);

        return ResponseEntity.ok(BaseResponse.<List<ChapterLiteDTO>>builder()
                .success(true)
                .data(chapterCrudPlugin.getChaptersByComicId(comicId, includeUnpublished))
                .build());
    }

    private void notifyTeamOfNewChapters(ComicEntity comic, String title) {
        if (comic == null) return;
        java.util.List<com.sep.comiverse.entity.ProjectTeamEntity> teams = 
                projectTeamRepository.findAllByComicNameIgnoreCase(comic.getTitle());
        for (com.sep.comiverse.entity.ProjectTeamEntity team : teams) {
            if (team.getLeaderId() != null) {
                notificationService.notifyUser(
                        team.getLeaderId(),
                        "New chapter in Backlog",
                        "A new chapter '" + title + "' has been approved and added to the backlog of " + team.getTitle(),
                        "UPDATE",
                        com.sep.comiverse.entity.enums.NotificationPreferenceKey.TEAM_UPDATES
                );
            }
        }
    }
}
