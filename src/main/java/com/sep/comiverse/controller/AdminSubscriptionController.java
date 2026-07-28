package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.SubscriptionPlanRequest;
import com.sep.comiverse.dto.request.UpdateSubscriptionPlanStatusRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.PaymentLogPageResponse;
import com.sep.comiverse.dto.response.SubscriptionPlanResponse;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.service.StripeSubscriptionService;
import com.sep.comiverse.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSubscriptionController {
    private final SubscriptionPlanService planService;
    private final StripeSubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<BaseResponse<List<SubscriptionPlanResponse>>> getPlans() {
        return ResponseEntity.ok(BaseResponse.<List<SubscriptionPlanResponse>>builder()
                .success(true)
                .data(planService.getAllPlans())
                .build());
    }

    @PostMapping("/plans")
    public ResponseEntity<BaseResponse<SubscriptionPlanResponse>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.<SubscriptionPlanResponse>builder()
                .success(true)
                .message("Subscription plan created")
                .data(planService.createPlan(request))
                .build());
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<BaseResponse<SubscriptionPlanResponse>> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody SubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.<SubscriptionPlanResponse>builder()
                .success(true)
                .message("Subscription plan updated. Pricing changes apply to new checkouts only.")
                .data(planService.updatePlan(planId, request))
                .build());
    }

    @PatchMapping("/plans/{planId}/status")
    public ResponseEntity<BaseResponse<SubscriptionPlanResponse>> updatePlanStatus(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateSubscriptionPlanStatusRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.<SubscriptionPlanResponse>builder()
                .success(true)
                .message("Subscription plan status updated")
                .data(planService.updateStatus(planId, request.getActive()))
                .build());
    }

    @GetMapping("/payments")
    public ResponseEntity<BaseResponse<PaymentLogPageResponse>> getPaymentLogs(
            @RequestParam(required = false) PaymentTransactionStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.<PaymentLogPageResponse>builder()
                .success(true)
                .data(subscriptionService.getPaymentLogs(status, query, page, size))
                .build());
    }
}
