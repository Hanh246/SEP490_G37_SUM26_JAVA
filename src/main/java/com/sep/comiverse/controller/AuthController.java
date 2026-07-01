package com.sep.comiverse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.dto.request.ForgotPasswordRequest;
import com.sep.comiverse.dto.request.ResetPasswordRequest;
import com.sep.comiverse.dto.request.ChangePasswordRequest;
import com.sep.comiverse.dto.request.UpdateProfileRequest;
import com.sep.comiverse.dto.response.AuthResponse;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sep.comiverse.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        UserEntity user = authService.authenticate(request.getUsername(), request.getPassword());
        String token = jwtTokenUtil.generateToken(user);

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName(),
                user.getDateOfBirth(),
                user.getAvatarUrl()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = authService.register(request);
        String token = jwtTokenUtil.generateToken(user);

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName(),
                user.getDateOfBirth(),
                user.getAvatarUrl()
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("An OTP code has been sent to your email.")
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
    public ResponseEntity<AuthResponse> registerStaff(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = authService.registerStaff(request);
        return ResponseEntity.ok(new AuthResponse(
                null,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName(),
                user.getDateOfBirth(),
                user.getAvatarUrl()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<AuthResponse>> getMyProfile(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            return ResponseEntity.status(401).body(BaseResponse.<AuthResponse>builder().success(false).message("Unauthorized").build());
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserEntity user = principal.user();
        
        AuthResponse response = new AuthResponse(
                null,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName(),
                user.getDateOfBirth(),
                user.getAvatarUrl()
        );
        return ResponseEntity.ok(BaseResponse.<AuthResponse>builder()
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
    public ResponseEntity<BaseResponse<AuthResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        UserEntity updatedUser = authService.updateProfile(principal.getId(), request.getFullName(), request.getAvatarUrl());
        AuthResponse response = new AuthResponse(
                null,
                updatedUser.getId(),
                updatedUser.getUsername(),
                updatedUser.getFullName(),
                updatedUser.getEmail(),
                updatedUser.getRole().getRoleName(),
                updatedUser.getDateOfBirth(),
                updatedUser.getAvatarUrl()
        );
        return ResponseEntity.ok(BaseResponse.<AuthResponse>builder()
                .success(true)
                .data(response)
                .message("Profile updated successfully.")
                .build());
    }
}
