package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.RejectPayoutRequest;
import com.sep.comiverse.dto.response.AdminPayoutPageResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.service.CreatorPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminPayoutController {

    private final CreatorPayoutService creatorPayoutService;

    @GetMapping
    public ResponseEntity<BaseResponse<AdminPayoutPageResponse>> getPayouts(
            @RequestParam(required = false) CreatorPayoutStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.<AdminPayoutPageResponse>builder()
                .success(true)
                .data(creatorPayoutService.getAdminPayouts(status, page, size))
                .build());
    }

    @PostMapping("/{payoutId}/approve")
    public ResponseEntity<BaseResponse<CreatorPayoutRequestResponse>> approve(
            @PathVariable UUID payoutId,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutRequestResponse>builder()
                .success(true)
                .message("Payout request approved")
                .data(creatorPayoutService.approve(payoutId, note))
                .build());
    }

    @PostMapping("/{payoutId}/reject")
    public ResponseEntity<BaseResponse<CreatorPayoutRequestResponse>> reject(
            @PathVariable UUID payoutId,
            @Valid @RequestBody RejectPayoutRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutRequestResponse>builder()
                .success(true)
                .message("Payout request rejected")
                .data(creatorPayoutService.reject(payoutId, request.getReason()))
                .build());
    }

    @PostMapping("/{payoutId}/pay")
    public ResponseEntity<BaseResponse<CreatorPayoutRequestResponse>> pay(
            @PathVariable UUID payoutId
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutRequestResponse>builder()
                .success(true)
                .message("Stripe sandbox transfer completed")
                .data(creatorPayoutService.payWithStripe(payoutId))
                .build());
    }
}
