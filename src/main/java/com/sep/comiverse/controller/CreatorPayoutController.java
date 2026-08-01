package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.request.LinkPayoutAccountRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.CreatorPayoutAccountResponse;
import com.sep.comiverse.dto.response.CreatorPayoutOverviewResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.CreatorPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/creator/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUTHOR', 'TRANSLATOR')")
public class CreatorPayoutController {

    private final CreatorPayoutService creatorPayoutService;

    @GetMapping("/overview")
    public ResponseEntity<BaseResponse<CreatorPayoutOverviewResponse>> getOverview(
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutOverviewResponse>builder()
                .success(true)
                .data(creatorPayoutService.getOverview(principal, month))
                .build());
    }

    @PutMapping("/account")
    public ResponseEntity<BaseResponse<CreatorPayoutAccountResponse>> linkAccount(
            @Valid @RequestBody LinkPayoutAccountRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutAccountResponse>builder()
                .success(true)
                .message("Stripe sandbox connected account linked")
                .data(creatorPayoutService.linkPayoutAccount(principal, request))
                .build());
    }

    @PostMapping("/requests")
    public ResponseEntity<BaseResponse<CreatorPayoutRequestResponse>> createRequest(
            @Valid @RequestBody CreatePayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<CreatorPayoutRequestResponse>builder()
                .success(true)
                .message("Monthly payout request submitted")
                .data(creatorPayoutService.createRequest(principal, request))
                .build());
    }
}
