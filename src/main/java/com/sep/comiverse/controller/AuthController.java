package com.sep.comiverse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.dto.request.ForgotPasswordRequest;
import com.sep.comiverse.dto.request.ResetPasswordRequest;
import com.sep.comiverse.dto.request.RefreshTokenRequest;
import com.sep.comiverse.dto.request.ChangePasswordRequest;
import com.sep.comiverse.dto.request.UpdateProfileRequest;
import com.sep.comiverse.dto.request.VerifyEmailRequest;
import com.sep.comiverse.dto.request.ReplaceLoginDeviceRequest;
import com.sep.comiverse.dto.request.ConfirmLoginDeviceRevocationRequest;
import com.sep.comiverse.dto.response.AuthResponse;
import com.sep.comiverse.dto.response.DeviceOtpChallengeResponse;
import com.sep.comiverse.dto.response.LoginDeviceResponse;
import com.sep.comiverse.dto.response.UserProfileResponse;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthService;
import com.sep.comiverse.service.PremiumPlanService;
import com.sep.comiverse.service.LoginDeviceService;
import com.sep.comiverse.repository.IUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sep.comiverse.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;
    private final PremiumPlanService premiumPlanService;
    private final LoginDeviceService loginDeviceService;
    private final IUserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        UserEntity user = authService.authenticate(request.getUsername(), request.getPassword());
        LoginDeviceService.LoginDecision decision = loginDeviceService.beginLogin(user, request);
        if (decision.verificationRequired()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuthResponse.builder()
                    .deviceVerificationRequired(true)
                    .deviceChallengeId(decision.challengeId())
                    .deviceChallengeExpiresAt(decision.expiresAt())
                    .devices(decision.devices())
                    .build());
        }
        String token = jwtTokenUtil.generateToken(user, decision.deviceId());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user, decision.deviceId());

        return ResponseEntity.ok(new AuthResponse(token, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken().trim();
        try {
            if (!jwtTokenUtil.validateJwtToken(refreshToken) || !jwtTokenUtil.isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("Invalid refresh token");
            }
            UUID userId = UUID.fromString(jwtTokenUtil.getSubjectFromJwtToken(refreshToken));
            UserEntity user = userRepository.findByIdWithRole(userId).orElseThrow();
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new IllegalStateException("Account is not active");
            }
            UUID loginDeviceId = jwtTokenUtil.getLoginDeviceIdFromToken(refreshToken);
            if (loginDeviceId != null && !loginDeviceService.isActive(userId, loginDeviceId)) {
                throw new IllegalStateException("Login device is no longer active");
            }
            return ResponseEntity.ok(new AuthResponse(
                    jwtTokenUtil.generateToken(user, loginDeviceId),
                    jwtTokenUtil.generateRefreshToken(user, loginDeviceId)
            ));
        } catch (Exception exception) {
            throw new CustomException(401, "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/devices/replace")
    public ResponseEntity<AuthResponse> replaceLoginDevice(
            @Valid @RequestBody ReplaceLoginDeviceRequest request
    ) {
        if (request.getChallengeId() == null || request.getDeviceToRemoveId() == null) {
            throw new CustomException(400, "Challenge and device to remove are required", HttpStatus.BAD_REQUEST);
        }
        LoginDeviceService.LoginDecision decision = loginDeviceService.confirmReplacement(
                request.getChallengeId(), request.getDeviceToRemoveId(), request.getOtp());
        UserEntity user = userRepository.findByIdWithRole(decision.userId()).orElseThrow(() ->
                new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(new AuthResponse(
                jwtTokenUtil.generateToken(user, decision.deviceId()),
                jwtTokenUtil.generateRefreshToken(user, decision.deviceId())
        ));
    }

    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<List<LoginDeviceResponse>>> listLoginDevices(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        UUID currentDeviceId = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            currentDeviceId = jwtTokenUtil.getLoginDeviceIdFromToken(authorization.substring(7));
        }
        return ResponseEntity.ok(BaseResponse.<List<LoginDeviceResponse>>builder()
                .success(true)
                .data(loginDeviceService.list(principal.getId(), currentDeviceId))
                .build());
    }

    @PostMapping("/devices/{deviceId}/revoke-otp")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<DeviceOtpChallengeResponse>> requestLoginDeviceRevocation(
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<DeviceOtpChallengeResponse>builder()
                .success(true)
                .data(loginDeviceService.requestRevocation(principal.getId(), deviceId))
                .message("Device verification OTP sent")
                .build());
    }

    @PostMapping("/devices/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<Void>> confirmLoginDeviceRevocation(
            @Valid @RequestBody ConfirmLoginDeviceRevocationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (request.getChallengeId() == null) {
            throw new CustomException(400, "Device challenge is required", HttpStatus.BAD_REQUEST);
        }
        loginDeviceService.confirmRevocation(principal.getId(), request.getChallengeId(), request.getOtp());
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Login device removed")
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);

        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Account created. Please check your email for the verification OTP code.")
                .build());
    }

    @PostMapping("/verify-email")
    public ResponseEntity<BaseResponse<String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Email verified successfully. You can now sign in.")
                .build());
    }

    @PostMapping("/resend-verification-otp")
    public ResponseEntity<BaseResponse<String>> resendVerificationOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendEmailVerificationOtp(request.getEmail());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("If this email is pending verification, we have sent a new OTP code.")
                .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("If this email exists in our system, we have sent password reset instructions.")
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Password reset successful. You can now log in.")
                .build());
    }

    @PostMapping("/register-staff")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AuthResponse> registerStaff(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        authService.registerStaff(request, principal == null ? null : principal.getId());
        return ResponseEntity.ok(new AuthResponse(null, null));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMyProfile(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(401).body(BaseResponse.<UserProfileResponse>builder().success(false).message("Unauthorized").build());
        }
        UserProfileResponse response = premiumPlanService.toUserProfileResponse(principal.user());
        return ResponseEntity.ok(BaseResponse.<UserProfileResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        authService.changePassword(principal.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Password updated successfully.")
                .build());
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        UserEntity updatedUser = authService.updateProfile(
                principal.getId(),
                request.getFullName(),
                request.getAvatarUrl(),
                request.getBackgroundImageUrl(),
                request.getDateOfBirth(),
                request.getBio()
        );
        UserProfileResponse response = premiumPlanService.toUserProfileResponse(updatedUser);
        return ResponseEntity.ok(BaseResponse.<UserProfileResponse>builder()
                .success(true)
                .data(response)
                .message("Profile updated successfully.")
                .build());
    }
}
