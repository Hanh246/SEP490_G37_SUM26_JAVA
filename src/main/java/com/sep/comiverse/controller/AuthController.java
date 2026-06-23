package com.sep.comiverse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.dto.request.ForgotPasswordRequest;
import com.sep.comiverse.dto.request.ResetPasswordRequest;
import com.sep.comiverse.dto.response.AuthResponse;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.security.JwtTokenUtil;
import com.sep.comiverse.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        UserEntity user = authService.authenticate(request.getUsername(), request.getPassword());
        String token = jwtTokenUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = authService.register(request);
        String token = jwtTokenUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getRoleName()
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Mã OTP đã được gửi đến email của bạn.")
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .message("Đổi mật khẩu thành công. Bạn có thể đăng nhập lại.")
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
                user.getRole().getRoleName()
        ));
    }
}
