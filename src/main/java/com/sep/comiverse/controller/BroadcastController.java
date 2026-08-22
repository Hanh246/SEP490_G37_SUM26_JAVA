package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.BroadcastRequest;
import com.sep.comiverse.dto.response.BroadcastAudiencePreviewResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.BroadcastResponse;
import com.sep.comiverse.service.BroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/broadcasts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - Broadcast", description = "APIs for sending announcements to all users, roles, accounts, or project teams (ADMIN only)")
public class BroadcastController {

    private final BroadcastService broadcastService;

    /**
     * POST /admin/broadcasts
     * Send a broadcast announcement to the selected audience.
     */
    @PostMapping
    @Operation(summary = "Send broadcast", description = "Create and send a broadcast announcement to all users, roles, specific accounts, or project teams")
    public ResponseEntity<BaseResponse<BroadcastResponse>> sendBroadcast(
            @Valid @RequestBody BroadcastRequest request
    ) {
        BroadcastResponse response = broadcastService.sendBroadcast(request);
        return ResponseEntity.ok(
                BaseResponse.<BroadcastResponse>builder()
                        .success(true)
                        .message("Broadcast sent successfully to " + response.getRecipientCount() + " users.")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview broadcast audience", description = "Count matched recipients and notification opt-outs before sending")
    public ResponseEntity<BaseResponse<BroadcastAudiencePreviewResponse>> previewAudience(
            @Valid @RequestBody BroadcastRequest request
    ) {
        return ResponseEntity.ok(
                BaseResponse.<BroadcastAudiencePreviewResponse>builder()
                        .success(true)
                        .data(broadcastService.previewAudience(request))
                        .build()
        );
    }

    /**
     * GET /admin/broadcasts
     * Retrieve the history of all sent broadcasts.
     */
    @GetMapping
    @Operation(summary = "Get broadcast history", description = "Retrieve list of all past broadcast announcements with recipient counts")
    public ResponseEntity<BaseResponse<List<BroadcastResponse>>> getBroadcastHistory() {
        List<BroadcastResponse> history = broadcastService.getBroadcastHistory();
        return ResponseEntity.ok(
                BaseResponse.<List<BroadcastResponse>>builder()
                        .success(true)
                        .data(history)
                        .build()
        );
    }

    /**
     * DELETE /admin/broadcasts/{id}
     * Revoke / recall a sent broadcast by ID.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke broadcast", description = "Recall a sent broadcast announcement and delete all associated notifications")
    public ResponseEntity<BaseResponse<Void>> revokeBroadcast(@PathVariable java.util.UUID id) {
        broadcastService.revokeBroadcast(id);
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Broadcast has been recalled successfully.")
                        .build()
        );
    }
}
