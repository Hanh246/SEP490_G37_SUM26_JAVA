package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.NotificationResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "User - Notifications", description = "APIs for user notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieve list of notifications for the currently logged-in user")
    public ResponseEntity<BaseResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<NotificationResponse> list = notificationService.getNotificationsForUser(principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<List<NotificationResponse>>builder()
                        .success(true)
                        .data(list)
                        .build()
        );
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Get number of unread notifications for the user")
    public ResponseEntity<BaseResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        long count = notificationService.getUnreadCountForUser(principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Long>builder()
                        .success(true)
                        .data(count)
                        .build()
        );
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Mark a single notification as read")
    public ResponseEntity<BaseResponse<Void>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        notificationService.markAsRead(id, principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Notification marked as read")
                        .build()
        );
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all as read", description = "Mark all notifications for the user as read")
    public ResponseEntity<BaseResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        notificationService.markAllAsRead(principal.getId());
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("All notifications marked as read")
                        .build()
        );
    }
}
