package com.sep.comiverse.service;

import lombok.RequiredArgsConstructor;
import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.exception.EmailVerificationRequiredException;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.EmailUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int EMAIL_VERIFICATION_OTP_TTL_MINUTES = 5;
    private static final int PASSWORD_RESET_OTP_TTL_MINUTES = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int OTP_MAX_SENDS_PER_HOUR = 5;
    private static final int OTP_MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final Map<String, OtpThrottleState> EMAIL_VERIFICATION_THROTTLE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> EMAIL_VERIFICATION_FAILURES = new ConcurrentHashMap<>();
    private static final Map<String, OtpThrottleState> PASSWORD_RESET_THROTTLE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PASSWORD_RESET_FAILURES = new ConcurrentHashMap<>();

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailUtil emailUtil;
    private final AuthorLicenseService authorLicenseService;

    public UserEntity authenticate(String username, String password) {
        UserEntity user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new CustomException(401, "Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(401, "Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
        if ("INACTIVE".equals(user.getStatus())) {
            throw new CustomException(403, "Your account has been banned!", HttpStatus.FORBIDDEN);
        }
        if ("PENDING_VERIFICATION".equals(user.getStatus())) {
            throw new EmailVerificationRequiredException();
        }
        return user;
    }

    @Transactional
    public UserEntity linkVerifiedGoogleIdentity(UserEntity user, String providerId, String picture) {
        String status = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase();
        if ("INACTIVE".equals(status) || "BANNED".equals(status)) {
            throw new CustomException(403, "Your account has been banned!", HttpStatus.FORBIDDEN);
        }
        if (!"ACTIVE".equals(status) && !"PENDING_VERIFICATION".equals(status)) {
            throw new CustomException(403, "This account cannot sign in.", HttpStatus.FORBIDDEN);
        }

        if ("PENDING_VERIFICATION".equals(status)) {
            user.setStatus("ACTIVE");
            user.setEmailVerificationToken(null);
            user.setEmailVerificationExpiresAt(null);
            if (user.getEmail() != null) {
                clearEmailVerificationFailures(user.getEmail().trim().toLowerCase());
            }
        }

        user.setProviderId(providerId);
        if (picture != null && !picture.isBlank()
                && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
            user.setAvatarUrl(picture);
        }
        return userRepository.save(user);
    }

    @Transactional
    public UserEntity register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new CustomException(400, "Username already exists", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(400, "Email already exists", HttpStatus.BAD_REQUEST);
        }

        RoleEntity userRole = roleRepository.findByRoleName("READER")
                .orElseThrow(() -> new CustomException(500, "Role READER not found", HttpStatus.INTERNAL_SERVER_ERROR));

        UserEntity user = UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(email)
                .phone(request.getPhone())
                .role(userRole)
                .status("PENDING_VERIFICATION")
                .dateOfBirth(request.getDateOfBirth())
                .build();

        UserEntity savedUser = userRepository.save(user);
        issueEmailVerificationOtp(savedUser);
        return savedUser;
    }

    @Transactional
    public void verifyEmail(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException(400, "Invalid or expired verification code", HttpStatus.BAD_REQUEST));

        if (!"LOCAL".equalsIgnoreCase(user.getProvider())) {
            throw new CustomException(400, "Invalid or expired verification code", HttpStatus.BAD_REQUEST);
        }
        if ("ACTIVE".equalsIgnoreCase(user.getStatus())) {
            clearEmailVerificationFailures(normalizedEmail);
            return;
        }
        if (!"PENDING_VERIFICATION".equalsIgnoreCase(user.getStatus())) {
            throw new CustomException(400, "This account cannot be verified.", HttpStatus.BAD_REQUEST);
        }
        if (getEmailVerificationFailureCount(normalizedEmail) >= OTP_MAX_VERIFY_ATTEMPTS) {
            throw new CustomException(429, "Too many invalid OTP attempts. Please request a new code.", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (user.getEmailVerificationToken() == null || !user.getEmailVerificationToken().equals(hashOtp(otp.trim()))) {
            registerEmailVerificationFailure(normalizedEmail);
            throw new CustomException(400, "Invalid or expired verification code", HttpStatus.BAD_REQUEST);
        }
        if (user.getEmailVerificationExpiresAt() == null || user.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())) {
            user.setEmailVerificationToken(null);
            user.setEmailVerificationExpiresAt(null);
            userRepository.save(user);
            clearEmailVerificationFailures(normalizedEmail);
            throw new CustomException(400, "Verification code has expired. Please request a new code.", HttpStatus.BAD_REQUEST);
        }

        user.setStatus("ACTIVE");
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);
        clearEmailVerificationFailures(normalizedEmail);
    }

    @Transactional
    public void resendEmailVerificationOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        assertEmailVerificationThrottle(normalizedEmail);

        UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !"LOCAL".equalsIgnoreCase(user.getProvider()) || !"PENDING_VERIFICATION".equalsIgnoreCase(user.getStatus())) {
            return;
        }

        issueEmailVerificationOtp(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        assertPasswordResetThrottle(normalizedEmail);

        UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !"LOCAL".equalsIgnoreCase(user.getProvider())) {
            return;
        }

        String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
        user.setResetToken(hashOtp(otp));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_OTP_TTL_MINUTES));
        userRepository.save(user);

        emailUtil.sendOTP(user.getEmail(), otp, user.getFullName());
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException(400, "Invalid or expired OTP code", HttpStatus.BAD_REQUEST));

        if (!"LOCAL".equalsIgnoreCase(user.getProvider())) {
            throw new CustomException(400, "Invalid or expired OTP code", HttpStatus.BAD_REQUEST);
        }
        if (getPasswordResetFailureCount(normalizedEmail) >= OTP_MAX_VERIFY_ATTEMPTS) {
            throw new CustomException(429, "Too many invalid OTP attempts. Please request a new code.", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (user.getResetToken() == null || !user.getResetToken().equals(hashOtp(otp.trim()))) {
            registerPasswordResetFailure(normalizedEmail);
            throw new CustomException(400, "Invalid or expired OTP code", HttpStatus.BAD_REQUEST);
        }
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            user.setResetToken(null);
            user.setResetTokenExpiresAt(null);
            userRepository.save(user);
            clearPasswordResetFailures(normalizedEmail);
            throw new CustomException(400, "OTP code has expired. Please request a new code.", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);
        clearPasswordResetFailures(normalizedEmail);
    }

    @Transactional
    public UserEntity registerStaff(RegisterRequest request) {
        return registerStaff(request, null);
    }

    @Transactional
    public UserEntity registerStaff(RegisterRequest request, java.util.UUID createdByAdminId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(400, "Username already exists", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(400, "Email already exists", HttpStatus.BAD_REQUEST);
        }

        String roleParam = request.getRole();
        final String finalRoleName = (roleParam == null || roleParam.trim().isEmpty())
                ? "MODERATOR"
                : roleParam.trim().toUpperCase();

        RoleEntity targetRole = roleRepository.findByRoleName(finalRoleName)
                .orElseThrow(() -> new CustomException(400, "Role " + finalRoleName + " not found", HttpStatus.BAD_REQUEST));

        String assignedLanguagesStr = null;
        if (request.getAssignedLanguages() != null) {
            assignedLanguagesStr = String.join(",", request.getAssignedLanguages());
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone())
                .role(targetRole)
                // Account login remains ACTIVE. Author publishing permission is controlled separately
                // by AuthorEntity.licenseStatus.
                .status("ACTIVE")
                .dateOfBirth(request.getDateOfBirth())
                .assignedLanguages(assignedLanguagesStr)
                .build();

        UserEntity saved = userRepository.save(user);
        if ("AUTHOR".equalsIgnoreCase(finalRoleName)) {
            authorLicenseService.initializePendingLicenseAuthor(saved, createdByAdminId);
        }
        return saved;
    }

    @Transactional
    public void changePassword(java.util.UUID userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CustomException(400, "Current password incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserEntity updateProfile(
            java.util.UUID userId,
            String fullName,
            String avatarUrl,
            String backgroundImageUrl,
            java.time.LocalDate dateOfBirth,
            String bio
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        if (fullName != null && !fullName.trim().isEmpty()) {
            user.setFullName(fullName.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        if (backgroundImageUrl != null) {
            user.setBackgroundImageUrl(backgroundImageUrl);
        }
        user.setDateOfBirth(dateOfBirth);
        user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        return userRepository.save(user);
    }

    private void assertPasswordResetThrottle(String normalizedEmail) {
        Instant now = Instant.now();
        synchronized (PASSWORD_RESET_THROTTLE) {
            OtpThrottleState state = PASSWORD_RESET_THROTTLE.get(normalizedEmail);
            if (state == null || Duration.between(state.windowStartedAt, now).toHours() >= 1) {
                PASSWORD_RESET_THROTTLE.put(normalizedEmail, new OtpThrottleState(now, now, 1));
                return;
            }
            long secondsSinceLastSend = Duration.between(state.lastSentAt, now).getSeconds();
            if (secondsSinceLastSend < OTP_RESEND_COOLDOWN_SECONDS) {
                throw new CustomException(429, "Please wait before requesting another OTP.", HttpStatus.TOO_MANY_REQUESTS);
            }
            if (state.sentInWindow >= OTP_MAX_SENDS_PER_HOUR) {
                throw new CustomException(429, "Too many OTP requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
            }
            state.lastSentAt = now;
            state.sentInWindow++;
        }
    }

    private void assertEmailVerificationThrottle(String normalizedEmail) {
        assertOtpThrottle(EMAIL_VERIFICATION_THROTTLE, normalizedEmail);
    }

    private void assertOtpThrottle(Map<String, OtpThrottleState> throttleMap, String normalizedEmail) {
        Instant now = Instant.now();
        synchronized (throttleMap) {
            OtpThrottleState state = throttleMap.get(normalizedEmail);
            if (state == null || Duration.between(state.windowStartedAt, now).toHours() >= 1) {
                throttleMap.put(normalizedEmail, new OtpThrottleState(now, now, 1));
                return;
            }
            long secondsSinceLastSend = Duration.between(state.lastSentAt, now).getSeconds();
            if (secondsSinceLastSend < OTP_RESEND_COOLDOWN_SECONDS) {
                throw new CustomException(429, "Please wait before requesting another OTP.", HttpStatus.TOO_MANY_REQUESTS);
            }
            if (state.sentInWindow >= OTP_MAX_SENDS_PER_HOUR) {
                throw new CustomException(429, "Too many OTP requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
            }
            state.lastSentAt = now;
            state.sentInWindow++;
        }
    }

    private void issueEmailVerificationOtp(UserEntity user) {
        String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
        user.setEmailVerificationToken(hashOtp(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(EMAIL_VERIFICATION_OTP_TTL_MINUTES));
        userRepository.save(user);
        emailUtil.sendSignupOtp(user.getEmail(), otp, user.getFullName());
    }

    private int getEmailVerificationFailureCount(String normalizedEmail) {
        return EMAIL_VERIFICATION_FAILURES.getOrDefault(normalizedEmail, 0);
    }

    private void registerEmailVerificationFailure(String normalizedEmail) {
        EMAIL_VERIFICATION_FAILURES.merge(normalizedEmail, 1, Integer::sum);
    }

    private void clearEmailVerificationFailures(String normalizedEmail) {
        EMAIL_VERIFICATION_FAILURES.remove(normalizedEmail);
    }

    private int getPasswordResetFailureCount(String normalizedEmail) {
        return PASSWORD_RESET_FAILURES.getOrDefault(normalizedEmail, 0);
    }

    private void registerPasswordResetFailure(String normalizedEmail) {
        PASSWORD_RESET_FAILURES.merge(normalizedEmail, 1, Integer::sum);
    }

    private void clearPasswordResetFailures(String normalizedEmail) {
        PASSWORD_RESET_FAILURES.remove(normalizedEmail);
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CustomException(500, "Could not secure OTP code.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static class OtpThrottleState {
        private final Instant windowStartedAt;
        private Instant lastSentAt;
        private int sentInWindow;

        private OtpThrottleState(Instant windowStartedAt, Instant lastSentAt, int sentInWindow) {
            this.windowStartedAt = windowStartedAt;
            this.lastSentAt = lastSentAt;
            this.sentInWindow = sentInWindow;
        }
    }
}
