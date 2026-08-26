package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreateCheckoutSessionRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.CheckoutSessionResponse;
import com.sep.comiverse.dto.response.CheckoutStatusResponse;
import com.sep.comiverse.dto.response.PortalSessionResponse;
import com.sep.comiverse.dto.response.ReaderPaymentHistoryPageResponse;
import com.sep.comiverse.dto.response.ReaderSubscriptionResponse;
import com.sep.comiverse.dto.response.SubscriptionPlanResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.StripeSubscriptionService;
import com.sep.comiverse.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionPlanService planService;
    private final StripeSubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<BaseResponse<List<SubscriptionPlanResponse>>> getActivePlans() {
        return ResponseEntity.ok(BaseResponse.<List<SubscriptionPlanResponse>>builder()
                .success(true)
                .data(planService.getActivePlans())
                .build());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('READER')")
    public ResponseEntity<BaseResponse<CheckoutSessionResponse>> createCheckout(
            @Valid @RequestBody CreateCheckoutSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CheckoutSessionResponse response = subscriptionService.createCheckoutSession(principal.getId(), request.getPlanId());
        return ResponseEntity.ok(BaseResponse.<CheckoutSessionResponse>builder()
                .success(true)
                .message("Stripe sandbox checkout session created")
                .data(response)
                .build());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('READER')")
    public ResponseEntity<BaseResponse<ReaderSubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<ReaderSubscriptionResponse>builder()
                .success(true)
                .data(subscriptionService.getCurrentSubscription(principal.getId()))
                .build());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('READER')")
    public ResponseEntity<BaseResponse<ReaderPaymentHistoryPageResponse>> getMyPaymentHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.<ReaderPaymentHistoryPageResponse>builder()
                .success(true)
                .data(subscriptionService.getPaymentHistory(principal.getId(), page, size))
                .build());
    }

    @GetMapping("/checkout/{sessionId}")
    @PreAuthorize("hasAuthority('READER')")
    public ResponseEntity<BaseResponse<CheckoutStatusResponse>> getCheckoutStatus(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<CheckoutStatusResponse>builder()
                .success(true)
                .data(subscriptionService.getCheckoutStatus(principal.getId(), sessionId))
                .build());
    }

    @PostMapping("/portal")
    @PreAuthorize("hasAuthority('READER')")
    public ResponseEntity<BaseResponse<PortalSessionResponse>> createBillingPortal(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<PortalSessionResponse>builder()
                .success(true)
                .data(subscriptionService.createPortalSession(principal.getId()))
                .build());
    }
}
