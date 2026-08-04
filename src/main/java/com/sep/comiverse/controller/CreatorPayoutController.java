package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.request.CreateStripePayoutOnboardingRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.CreatorPayoutAccountResponse;
import com.sep.comiverse.dto.response.CreatorPayoutCurrencyResponse;
import com.sep.comiverse.dto.response.CreatorPayoutOverviewResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.dto.response.StripePayoutOnboardingLinkResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.CreatorPayoutService;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.CreatorStripePayoutProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/creator/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUTHOR', 'TRANSLATOR')")
public class CreatorPayoutController {

    private final CreatorPayoutService creatorPayoutService;
    private final CreatorStripePayoutProfileService payoutProfileService;
    private final CreatorPayoutSettingsService payoutSettingsService;

    @GetMapping({"", "/overview"})
    public ResponseEntity<BaseResponse<CreatorPayoutOverviewResponse>>
    getOverview(
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                BaseResponse.<CreatorPayoutOverviewResponse>builder()
                        .success(true)
                        .data(creatorPayoutService.getOverview(
                                principal,
                                month
                        ))
                        .build()
        );
    }

    @GetMapping("/currencies")
    public ResponseEntity<BaseResponse<List<CreatorPayoutCurrencyResponse>>>
    getCurrencies() {
        return ResponseEntity.ok(
                BaseResponse.<List<CreatorPayoutCurrencyResponse>>builder()
                        .success(true)
                        .data(payoutSettingsService.getSupportedCurrencies())
                        .build()
        );
    }

    @GetMapping("/account")
    public ResponseEntity<BaseResponse<CreatorPayoutAccountResponse>>
    getAccount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                BaseResponse.<CreatorPayoutAccountResponse>builder()
                        .success(true)
                        .data(payoutProfileService.getProfile(principal))
                        .build()
        );
    }

    @PostMapping("/account/onboarding")
    public ResponseEntity<BaseResponse<StripePayoutOnboardingLinkResponse>>
    createOnboardingLink(
            @Valid
            @RequestBody
            CreateStripePayoutOnboardingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                BaseResponse.<StripePayoutOnboardingLinkResponse>builder()
                        .success(true)
                        .message("Stripe onboarding link created")
                        .data(payoutProfileService.createOnboardingLink(
                                principal,
                                request
                        ))
                        .build()
        );
    }

    @PostMapping("/account/sync")
    public ResponseEntity<BaseResponse<CreatorPayoutAccountResponse>>
    syncAccount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                BaseResponse.<CreatorPayoutAccountResponse>builder()
                        .success(true)
                        .message("Stripe payout account synchronized")
                        .data(payoutProfileService.syncProfile(principal))
                        .build()
        );
    }

    @PostMapping("/requests")
    public ResponseEntity<BaseResponse<CreatorPayoutRequestResponse>>
    createRequest(
            @Valid
            @RequestBody
            CreatePayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                BaseResponse.<CreatorPayoutRequestResponse>builder()
                        .success(true)
                        .message("Monthly payout request submitted")
                        .data(creatorPayoutService.createRequest(
                                principal,
                                request
                        ))
                        .build()
        );
    }
}