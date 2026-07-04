package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.UpdatePremiumPlanSettingsRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.PremiumPlanSettingsResponse;
import com.sep.comiverse.service.PremiumPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSystemSettingsController {
    private final PremiumPlanService premiumPlanService;

    @GetMapping("/premium-plans")
    public ResponseEntity<BaseResponse<PremiumPlanSettingsResponse>> getPremiumPlanSettings() {
        return ResponseEntity.ok(BaseResponse.<PremiumPlanSettingsResponse>builder()
                .success(true)
                .data(premiumPlanService.getPremiumPlanSettings())
                .build());
    }

    @PutMapping("/premium-plans")
    public ResponseEntity<BaseResponse<PremiumPlanSettingsResponse>> updatePremiumPlanSettings(
            @Valid @RequestBody UpdatePremiumPlanSettingsRequest request
    ) {
        PremiumPlanSettingsResponse response = premiumPlanService.updatePremiumPlanSettings(request);
        return ResponseEntity.ok(BaseResponse.<PremiumPlanSettingsResponse>builder()
                .success(true)
                .message("Premium plan settings saved successfully.")
                .data(response)
                .build());
    }
}
