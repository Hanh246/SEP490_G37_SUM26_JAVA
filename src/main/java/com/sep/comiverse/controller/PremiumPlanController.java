package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.UpgradePlanRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.PremiumPlanSettingsResponse;
import com.sep.comiverse.dto.response.UpgradePlanResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.PremiumPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
public class PremiumPlanController {
    private final PremiumPlanService premiumPlanService;

    @GetMapping
    public ResponseEntity<BaseResponse<PremiumPlanSettingsResponse>> getPremiumPlans() {
        return ResponseEntity.ok(BaseResponse.<PremiumPlanSettingsResponse>builder()
                .success(true)
                .data(premiumPlanService.getPremiumPlanSettings())
                .build());
    }

    @PostMapping("/upgrade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<UpgradePlanResponse>> upgradePlan(
            @Valid @RequestBody UpgradePlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        throw new CustomException(
                402,
                "Direct premium upgrade is disabled. Create a verified Stripe Checkout session through /subscriptions/checkout.",
                HttpStatus.PAYMENT_REQUIRED
        );
    }
}
