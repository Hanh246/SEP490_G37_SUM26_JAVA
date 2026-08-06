package com.sep.comiverse.controller;

import com.sep.comiverse.dto.AppealResolveRequestDTO;
import com.sep.comiverse.dto.AppealTicketRequestDTO;
import com.sep.comiverse.dto.AppealTicketResponseDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.AppealService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/appeals")
@RequiredArgsConstructor
@Tag(name = "Appeal Management", description = "Endpoints for authors to submit appeals and moderators to resolve them")
public class AppealController {

    private final AppealService appealService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER', 'AUTHOR')")
    @Operation(summary = "Submit a new appeal ticket (Author only)")
    public ResponseEntity<BaseResponse<AppealTicketResponseDTO>> createAppeal(
            @Valid @RequestBody AppealTicketRequestDTO requestDTO,
            @AuthenticationPrincipal com.sep.comiverse.security.UserPrincipal principal
    ) {
        UUID authorId = principal.getId();
        AppealTicketResponseDTO response = appealService.createAppeal(authorId, requestDTO);
        return ResponseEntity.ok(
                BaseResponse.<AppealTicketResponseDTO>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/my-appeals")
    @PreAuthorize("hasAnyAuthority('USER', 'AUTHOR')")
    @Operation(summary = "Get current user's appeal history")
    public ResponseEntity<BaseResponse<Page<AppealTicketResponseDTO>>> getMyAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal com.sep.comiverse.security.UserPrincipal principal
    ) {
        UUID authorId = principal.getId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AppealTicketResponseDTO> response = appealService.getAppealsByAuthor(authorId, pageable);
        return ResponseEntity.ok(
                BaseResponse.<Page<AppealTicketResponseDTO>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get all pending appeals for moderation queue")
    public ResponseEntity<BaseResponse<Page<AppealTicketResponseDTO>>> getPendingAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<AppealTicketResponseDTO> response = appealService.getPendingAppeals(pageable);
        return ResponseEntity.ok(
                BaseResponse.<Page<AppealTicketResponseDTO>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/target/{targetId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get pending appeal by target ID")
    public ResponseEntity<BaseResponse<AppealTicketResponseDTO>> getPendingAppealByTarget(
            @PathVariable UUID targetId
    ) {
        AppealTicketResponseDTO response = appealService.getPendingAppealByTargetId(targetId);
        return ResponseEntity.ok(
                BaseResponse.<AppealTicketResponseDTO>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
    @Operation(summary = "Resolve a pending appeal ticket")
    public ResponseEntity<BaseResponse<AppealTicketResponseDTO>> resolveAppeal(
            @PathVariable UUID id,
            @Valid @RequestBody AppealResolveRequestDTO requestDTO,
            @AuthenticationPrincipal com.sep.comiverse.security.UserPrincipal principal
    ) {
        UUID moderatorId = principal.getId();
        AppealTicketResponseDTO response = appealService.resolveAppeal(id, moderatorId, requestDTO);
        return ResponseEntity.ok(
                BaseResponse.<AppealTicketResponseDTO>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }
}
