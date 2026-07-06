package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.scheduler.UserInteractionSyncScheduler;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
@Tag(name = "Sync Schedulers", description = "Manual triggers for sync schedulers")
public class SyncController {

    private final UserInteractionSyncScheduler userInteractionSyncScheduler;
    private final ViewSyncScheduler viewSyncScheduler;

    @PostMapping("/interactions")
    @Operation(summary = "Manually trigger UserInteractionSyncScheduler")
    public ResponseEntity<BaseResponse<Void>> syncInteractions() {
        userInteractionSyncScheduler.flushInteractionsToPostgres();
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Manually triggered user interaction sync scheduler")
                        .build()
        );
    }

    @PostMapping("/views")
    @Operation(summary = "Manually trigger ViewSyncScheduler")
    public ResponseEntity<BaseResponse<Void>> syncViews() {
        viewSyncScheduler.flushViewsToPostgres();
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Manually triggered view sync scheduler")
                        .build()
        );
    }

    @DeleteMapping("/reading-history")
    @Operation(summary = "Manually trigger ViewSyncScheduler")
    public ResponseEntity<BaseResponse<Void>> syncDeleteReadHistory() {
        viewSyncScheduler.cleanOldReadingHistories();
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Manually triggered view sync scheduler")
                        .build()
        );
    }
}
