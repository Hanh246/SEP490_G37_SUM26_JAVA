package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AuthService;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.util.EmailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private IUserRepository userRepository;
    @Mock
    private IRoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private AuthorLicenseService authorLicenseService;

    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        clearStaticMap("EMAIL_VERIFICATION_THROTTLE");
        clearStaticMap("EMAIL_VERIFICATION_FAILURES");
        clearStaticMap("PASSWORD_RESET_THROTTLE");
        clearStaticMap("PASSWORD_RESET_FAILURES");
        service = new AuthService(userRepository, roleRepository, passwordEncoder, emailUtil, authorLicenseService);
    }

    // ===== authenticate: one decision-table row per independent condition =====

    @Test
    void authenticateReturnsAnActiveUserForValidCredentials() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

        UserEntity result = service.authenticate("reader", "secret");

        assertSame(user, result);
    }

    @Test
    void authenticateRejectsUnknownAccountWithGenericUnauthorizedError() {
        when(userRepository.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.authenticate("missing", "secret")
        );

        assertEquals("Invalid username or password", error.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticateRejectsWrongPasswordWithSameGenericUnauthorizedError() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.authenticate("reader", "wrong")
        );

        assertEquals("Invalid username or password", error.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
    }

    @Test
    void authenticateRejectsInactiveAccount() {
        UserEntity user = localUser("reader@example.com", "INACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.authenticate("reader", "secret")
        );

        assertEquals("Your account has been banned!", error.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, error.getHttpStatus());
    }

    @Test
    void authenticateRejectsPendingVerificationAccount() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.authenticate("reader", "secret")
        );

        assertEquals("Please verify your email before signing in.", error.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, error.getHttpStatus());
    }

    // ===== register =====

    @Test
    void registerNormalizesIdentityCreatesPendingReaderAndSendsOtp() {
        RegisterRequest request = registerRequest();
        RoleEntity readerRole = RoleEntity.builder().roleName("READER").build();
        when(roleRepository.findByRoleName("READER")).thenReturn(Optional.of(readerRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime before = LocalDateTime.now();

        UserEntity result = service.register(request);

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailUtil).sendSignupOtp(eq("reader@example.com"), otpCaptor.capture(), eq("Reader One"));
        verify(userRepository, times(2)).save(result);
        assertEquals("reader.one", result.getUsername());
        assertEquals("reader@example.com", result.getEmail());
        assertEquals("encoded-password", result.getPassword());
        assertEquals("PENDING_VERIFICATION", result.getStatus());
        assertSame(readerRole, result.getRole());
        assertTrue(otpCaptor.getValue().matches("\\d{6}"));
        assertEquals(sha256(otpCaptor.getValue()), result.getEmailVerificationToken());
        assertTrue(result.getEmailVerificationExpiresAt().isAfter(before.plusMinutes(4)));
        assertTrue(result.getEmailVerificationExpiresAt().isBefore(before.plusMinutes(6)));
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeCreatingAnything() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername("reader.one")).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () -> service.register(request));

        assertEquals("Username already exists", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(roleRepository, passwordEncoder, emailUtil);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmailBeforeCreatingAnything() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername("reader.one")).thenReturn(false);
        when(userRepository.existsByEmail("reader@example.com")).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () -> service.register(request));

        assertEquals("Email already exists", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(roleRepository, passwordEncoder, emailUtil);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerFailsWhenReaderRoleConfigurationIsMissing() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername("reader.one")).thenReturn(false);
        when(userRepository.existsByEmail("reader@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("READER")).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class, () -> service.register(request));

        assertEquals("Role READER not found", error.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getHttpStatus());
        verifyNoInteractions(passwordEncoder, emailUtil);
        verify(userRepository, never()).save(any());
    }

    // ===== verifyEmail: lifecycle and OTP conditions are separate scenarios =====

    @Test
    void verifyEmailActivatesPendingLocalAccountAndConsumesOtp() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.verifyEmail("reader@example.com", otp);

        assertEquals("ACTIVE", user.getStatus());
        assertNull(user.getEmailVerificationToken());
        assertNull(user.getEmailVerificationExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailNormalizesEmailBeforeLookup() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.verifyEmail("  Reader@Example.COM  ", otp);

        verify(userRepository).findByEmail("reader@example.com");
        assertEquals("ACTIVE", user.getStatus());
    }

    @Test
    void verifyEmailAcceptsOtpWithSurroundingWhitespace() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.verifyEmail("reader@example.com", "  " + otp + "  ");

        assertEquals("ACTIVE", user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailIsIdempotentForAlreadyActiveLocalAccount() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.verifyEmail("reader@example.com", "123456");

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("missing@example.com", "123456")
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsNonLocalAccount() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setProvider("GOOGLE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", "123456")
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsAccountInInvalidLifecycleState() {
        UserEntity user = localUser("reader@example.com", "INACTIVE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", "123456")
        );

        assertEquals("This account cannot be verified.", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }

    @Test
    void verifyEmailRejectsWrongOtpWithoutConsumingStoredOtp() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256("123456"));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", "000000")
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        assertEquals(sha256("123456"), user.getEmailVerificationToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsWhenStoredOtpTokenIsMissing() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", "123456")
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        assertNull(user.getEmailVerificationToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailRejectsAfterMaximumInvalidAttempts() throws Exception {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256("123456"));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        staticMap("EMAIL_VERIFICATION_FAILURES").put("reader@example.com", 5);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", "123456")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailAllowsAttemptOneBelowMaximumThreshold() throws Exception {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        staticMap("EMAIL_VERIFICATION_FAILURES").put("reader@example.com", 4);

        service.verifyEmail("reader@example.com", otp);

        assertEquals("ACTIVE", user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailRejectsAndConsumesAnExpiredOtp() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", otp)
        );

        assertEquals("Verification code has expired. Please request a new code.", error.getMessage());
        assertNull(user.getEmailVerificationToken());
        assertNull(user.getEmailVerificationExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailRejectsAndConsumesOtpWhenExpiryIsMissing() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(null);
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.verifyEmail("reader@example.com", otp)
        );

        assertEquals("Verification code has expired. Please request a new code.", error.getMessage());
        assertNull(user.getEmailVerificationToken());
        assertNull(user.getEmailVerificationExpiresAt());
        verify(userRepository).save(user);
    }

    // ===== resend verification =====

    @Test
    void resendEmailVerificationOtpIssuesNewOtpForPendingLocalAccount() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.resendEmailVerificationOtp(" Reader@Example.com ");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailUtil).sendSignupOtp(eq("reader@example.com"), otpCaptor.capture(), eq("Reader One"));
        assertEquals(sha256(otpCaptor.getValue()), user.getEmailVerificationToken());
        verify(userRepository).save(user);
    }

    @Test
    void resendEmailVerificationOtpDoesNotRevealUnknownAccount() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.resendEmailVerificationOtp("missing@example.com");

        verifyNoInteractions(emailUtil);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendEmailVerificationOtpSkipsNonLocalAccountWithoutSendingMail() {
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setProvider("GOOGLE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.resendEmailVerificationOtp("reader@example.com");

        verifyNoInteractions(emailUtil);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendEmailVerificationOtpSkipsAlreadyActiveAccountWithoutSendingMail() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.resendEmailVerificationOtp("reader@example.com");

        verifyNoInteractions(emailUtil);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendEmailVerificationOtpRejectsRequestInsideCooldown() throws Exception {
        String email = "reader@example.com";
        putThrottleState("EMAIL_VERIFICATION_THROTTLE", email,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(30), 1);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resendEmailVerificationOtp(email)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verifyNoInteractions(userRepository, emailUtil);
    }

    @Test
    void resendEmailVerificationOtpRejectsAfterMaximumSendsPerHour() throws Exception {
        String email = "reader@example.com";
        putThrottleState("EMAIL_VERIFICATION_THROTTLE", email,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), 5);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resendEmailVerificationOtp(email)
        );

        assertEquals("Too many OTP requests. Please try again later.", error.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verifyNoInteractions(userRepository, emailUtil);
    }

    @Test
    void resendEmailVerificationOtpAllowsExistingWindowBelowMaximumAfterCooldown() throws Exception {
        String email = "reader@example.com";
        UserEntity user = localUser(email, "PENDING_VERIFICATION");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        putThrottleState("EMAIL_VERIFICATION_THROTTLE", email,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), 4);

        service.resendEmailVerificationOtp(email);

        verify(userRepository).findByEmail(email);
        verify(userRepository).save(user);
        verify(emailUtil).sendSignupOtp(eq(email), any(String.class), eq("Reader One"));
        assertEquals(5, throttleSentInWindow("EMAIL_VERIFICATION_THROTTLE", email));
    }

    @Test
    void resendEmailVerificationOtpResetsExpiredThrottleWindowBeforeApplyingCooldownOrCount() throws Exception {
        String email = "reader@example.com";
        UserEntity user = localUser(email, "PENDING_VERIFICATION");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        putThrottleState("EMAIL_VERIFICATION_THROTTLE", email,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(10), 5);

        service.resendEmailVerificationOtp(email);

        verify(userRepository).findByEmail(email);
        verify(userRepository).save(user);
        verify(emailUtil).sendSignupOtp(eq(email), any(String.class), eq("Reader One"));
        assertEquals(1, throttleSentInWindow("EMAIL_VERIFICATION_THROTTLE", email));
    }

    // ===== forgot/reset password =====

    @Test
    void forgotPasswordReturnsTheSameWayForAnUnknownEmailWithoutSendingMail() {
        String email = "missing-" + UUID.randomUUID() + "@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.forgotPassword(email);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailUtil);
    }

    @Test
    void forgotPasswordReturnsTheSameWayForNonLocalAccountWithoutSendingMail() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setProvider("GOOGLE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword("reader@example.com");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailUtil);
    }

    @Test
    void forgotPasswordStoresOnlyTheOtpHashForALocalAccount() {
        String email = "reader-" + UUID.randomUUID() + "@example.com";
        UserEntity user = localUser(email, "ACTIVE");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        service.forgotPassword(email);

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailUtil).sendOTP(eq(email), otpCaptor.capture(), eq("Reader One"));
        verify(userRepository).save(user);
        assertTrue(otpCaptor.getValue().matches("\\d{6}"));
        assertEquals(sha256(otpCaptor.getValue()), user.getResetToken());
        assertFalse(user.getResetToken().equals(otpCaptor.getValue()));
        assertTrue(user.getResetTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(4)));
    }

    @Test
    void forgotPasswordNormalizesEmailBeforeLookup() {
        String email = "reader@example.com";
        UserEntity user = localUser(email, "ACTIVE");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        service.forgotPassword("  READER@EXAMPLE.COM  ");

        verify(userRepository).findByEmail(email);
        verify(userRepository).save(user);
        verify(emailUtil).sendOTP(eq(email), any(String.class), eq("Reader One"));
    }

    @Test
    void forgotPasswordRejectsRequestInsideCooldown() throws Exception {
        String email = "reader@example.com";
        putThrottleState("PASSWORD_RESET_THROTTLE", email,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(30), 1);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.forgotPassword(email)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verifyNoInteractions(userRepository, emailUtil);
    }

    @Test
    void forgotPasswordRejectsAfterMaximumSendsPerHour() throws Exception {
        String email = "reader@example.com";
        putThrottleState("PASSWORD_RESET_THROTTLE", email,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), 5);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.forgotPassword(email)
        );

        assertEquals("Too many OTP requests. Please try again later.", error.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verifyNoInteractions(userRepository, emailUtil);
    }

    @Test
    void forgotPasswordAllowsExistingWindowBelowMaximumAfterCooldown() throws Exception {
        String email = "reader@example.com";
        UserEntity user = localUser(email, "ACTIVE");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        putThrottleState("PASSWORD_RESET_THROTTLE", email,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), 4);

        service.forgotPassword(email);

        verify(userRepository).save(user);
        verify(emailUtil).sendOTP(eq(email), any(String.class), eq("Reader One"));
        assertEquals(5, throttleSentInWindow("PASSWORD_RESET_THROTTLE", email));
    }

    @Test
    void forgotPasswordResetsExpiredThrottleWindowBeforeApplyingCooldownOrCount() throws Exception {
        String email = "reader@example.com";
        UserEntity user = localUser(email, "ACTIVE");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        putThrottleState("PASSWORD_RESET_THROTTLE", email,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(10), 5);

        service.forgotPassword(email);

        verify(userRepository).save(user);
        verify(emailUtil).sendOTP(eq(email), any(String.class), eq("Reader One"));
        assertEquals(1, throttleSentInWindow("PASSWORD_RESET_THROTTLE", email));
    }

    @Test
    void resetPasswordEncodesNewPasswordAndConsumesOtp() {
        String otp = "654321";
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256(otp));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded-password");

        service.resetPassword("reader@example.com", otp, "new-secret");

        assertEquals("new-encoded-password", user.getPassword());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordAcceptsOtpWithSurroundingWhitespace() {
        String otp = "654321";
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256(otp));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded-password");

        service.resetPassword("reader@example.com", "  " + otp + "  ", "new-secret");

        assertEquals("new-encoded-password", user.getPassword());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("missing@example.com", "123456", "new-secret")
        );

        assertEquals("Invalid or expired OTP code", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }

    @Test
    void resetPasswordRejectsNonLocalAccount() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setProvider("GOOGLE");
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", "123456", "new-secret")
        );

        assertEquals("Invalid or expired OTP code", error.getMessage());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPasswordRejectsInvalidOtpWithoutChangingPassword() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256("123456"));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", "000000", "new-secret")
        );

        assertEquals("Invalid or expired OTP code", error.getMessage());
        assertEquals("encoded-old-password", user.getPassword());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsWhenStoredResetTokenIsMissing() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(null);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", "123456", "new-secret")
        );

        assertEquals("Invalid or expired OTP code", error.getMessage());
        assertEquals("encoded-old-password", user.getPassword());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsExpiredOtpAndConsumesIt() {
        String otp = "654321";
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256(otp));
        user.setResetTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", otp, "new-secret")
        );

        assertEquals("OTP code has expired. Please request a new code.", error.getMessage());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiresAt());
        verify(userRepository).save(user);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPasswordRejectsAndConsumesTokenWhenExpiryIsMissing() {
        String otp = "654321";
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256(otp));
        user.setResetTokenExpiresAt(null);
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", otp, "new-secret")
        );

        assertEquals("OTP code has expired. Please request a new code.", error.getMessage());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiresAt());
        verify(userRepository).save(user);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPasswordRejectsAfterMaximumInvalidAttempts() throws Exception {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256("123456"));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        staticMap("PASSWORD_RESET_FAILURES").put("reader@example.com", 5);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.resetPassword("reader@example.com", "123456", "new-secret")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordAllowsAttemptOneBelowMaximumThreshold() throws Exception {
        String otp = "654321";
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setResetToken(sha256(otp));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded-password");
        staticMap("PASSWORD_RESET_FAILURES").put("reader@example.com", 4);

        service.resetPassword("reader@example.com", otp, "new-secret");

        assertEquals("new-encoded-password", user.getPassword());
        verify(userRepository).save(user);
    }

    // ===== registerStaff =====

    @Test
    void registerStaffDefaultsBlankRoleToActiveModerator() {
        RegisterRequest request = registerRequest();
        request.setRole("   ");
        RoleEntity moderatorRole = RoleEntity.builder().roleName("MODERATOR").build();
        when(roleRepository.findByRoleName("MODERATOR")).thenReturn(Optional.of(moderatorRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.registerStaff(request, UUID.randomUUID());

        assertEquals("ACTIVE", result.getStatus());
        assertSame(moderatorRole, result.getRole());
        verifyNoInteractions(authorLicenseService);
    }

    @Test
    void registerStaffDefaultsNullRoleToActiveModerator() {
        RegisterRequest request = registerRequest();
        request.setRole(null);
        RoleEntity moderatorRole = RoleEntity.builder().roleName("MODERATOR").build();
        when(roleRepository.findByRoleName("MODERATOR")).thenReturn(Optional.of(moderatorRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.registerStaff(request, UUID.randomUUID());

        assertEquals("ACTIVE", result.getStatus());
        assertSame(moderatorRole, result.getRole());
        verifyNoInteractions(authorLicenseService);
    }

    @Test
    void registerStaffJoinsAssignedLanguagesWhenProvided() {
        RegisterRequest request = registerRequest();
        request.setRole("MODERATOR");
        request.setAssignedLanguages(List.of("Korean", "Japanese"));
        RoleEntity moderatorRole = RoleEntity.builder().roleName("MODERATOR").build();
        when(roleRepository.findByRoleName("MODERATOR")).thenReturn(Optional.of(moderatorRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.registerStaff(request, UUID.randomUUID());

        assertEquals("Korean,Japanese", result.getAssignedLanguages());
        verifyNoInteractions(authorLicenseService);
    }

    @Test
    void registerStaffInitializesPendingLicenseWhenRoleIsAuthor() {
        RegisterRequest request = registerRequest();
        request.setRole(" author ");
        RoleEntity authorRole = RoleEntity.builder().roleName("AUTHOR").build();
        UUID adminId = UUID.randomUUID();
        when(roleRepository.findByRoleName("AUTHOR")).thenReturn(Optional.of(authorRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.registerStaff(request, adminId);

        assertEquals("ACTIVE", result.getStatus());
        assertSame(authorRole, result.getRole());
        verify(authorLicenseService).initializePendingLicenseAuthor(result, adminId);
    }

    @Test
    void registerStaffRejectsDuplicateUsernameBeforeCreatingAnything() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.registerStaff(request, UUID.randomUUID())
        );

        assertEquals("Username already exists", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(roleRepository, passwordEncoder, authorLicenseService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerStaffRejectsDuplicateEmailBeforeCreatingAnything() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.registerStaff(request, UUID.randomUUID())
        );

        assertEquals("Email already exists", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(roleRepository, passwordEncoder, authorLicenseService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerStaffFailsWhenRequestedRoleDoesNotExist() {
        RegisterRequest request = registerRequest();
        request.setRole("GHOST");
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByRoleName("GHOST")).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.registerStaff(request, UUID.randomUUID())
        );

        assertEquals("Role GHOST not found", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(passwordEncoder, authorLicenseService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerStaffWithoutAdminIdStillCreatesActiveStaffAccount() {
        RegisterRequest request = registerRequest();
        request.setRole("MODERATOR");
        RoleEntity moderatorRole = RoleEntity.builder().roleName("MODERATOR").build();
        when(roleRepository.findByRoleName("MODERATOR")).thenReturn(Optional.of(moderatorRole));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.registerStaff(request);

        assertEquals("ACTIVE", result.getStatus());
        assertSame(moderatorRole, result.getRole());
        verifyNoInteractions(authorLicenseService);
    }

    // ===== changePassword =====

    @Test
    void changePasswordRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.changePassword(userId, "old", "new-secret")
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changePasswordRequiresTheCurrentPassword() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.changePassword(userId, "wrong", "new-secret")
        );

        assertEquals("Current password incorrect", error.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordEncodesAndSavesNewPasswordWhenCurrentPasswordMatches() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("new-secret")).thenReturn("encoded-new");

        service.changePassword(userId, "old", "new-secret");

        assertEquals("encoded-new", user.getPassword());
        verify(userRepository).save(user);
    }

    // ===== updateProfile: one field partition per scenario =====

    @Test
    void updateProfileRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateProfile(userId, "Name", null, null, null, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileTrimsNonBlankFullName() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setFullName("Old Name");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserEntity result = service.updateProfile(userId, "  New Name  ", null, null, null, null);

        assertSame(user, result);
        assertEquals("New Name", user.getFullName());
    }

    @Test
    void updateProfileKeepsExistingFullNameWhenInputIsBlank() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setFullName("Old Name");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, "   ", null, null, null, null);

        assertEquals("Old Name", user.getFullName());
    }

    @Test
    void updateProfileSetsAvatarWhenProvided() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setAvatarUrl("old-avatar");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, "new-avatar", null, null, null);

        assertEquals("new-avatar", user.getAvatarUrl());
    }

    @Test
    void updateProfileKeepsExistingAvatarWhenInputIsNull() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setAvatarUrl("old-avatar");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, null, null, null);

        assertEquals("old-avatar", user.getAvatarUrl());
    }

    @Test
    void updateProfileSetsBackgroundImageUrlWhenProvided() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setBackgroundImageUrl("old-background");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, "new-background", null, null);

        assertEquals("new-background", user.getBackgroundImageUrl());
    }

    @Test
    void updateProfileKeepsExistingBackgroundImageUrlWhenInputIsNull() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setBackgroundImageUrl("old-background");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, null, null, null);

        assertEquals("old-background", user.getBackgroundImageUrl());
    }

    @Test
    void updateProfileClearsBlankBio() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setBio("Old bio");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, null, null, "   ");

        assertNull(user.getBio());
    }

    @Test
    void updateProfileTrimsNonBlankBio() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, null, null, "  New bio  ");

        assertEquals("New bio", user.getBio());
    }

    @Test
    void updateProfileSetsDateOfBirthWhenProvided() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        LocalDate newDateOfBirth = LocalDate.of(2001, 5, 20);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateProfile(userId, null, null, null, newDateOfBirth, null);

        assertEquals(newDateOfBirth, user.getDateOfBirth());
    }

    @Test
    void updateProfileOverwritesDateOfBirthWithNullWhenNotProvided() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setDateOfBirth(LocalDate.of(2001, 5, 20));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        // NOTE: unlike fullName/avatarUrl, dateOfBirth is unconditionally overwritten by
        // whatever is passed in, including null. This test pins down that existing behavior
        // so a future refactor cannot silently change it without a failing test.
        service.updateProfile(userId, null, null, null, null, null);

        assertNull(user.getDateOfBirth());
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("  reader.one  ");
        request.setPassword("secret123");
        request.setFullName("Reader One");
        request.setEmail("  Reader@Example.COM  ");
        request.setPhone("0912345678");
        request.setDateOfBirth(LocalDate.of(2001, 5, 20));
        return request;
    }

    private UserEntity localUser(String email, String status) {
        return UserEntity.builder()
                .username("reader")
                .password("encoded-old-password")
                .fullName("Reader One")
                .email(email)
                .provider("LOCAL")
                .status(status)
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new AssertionError("SHA-256 must be available for the test runtime", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> staticMap(String fieldName) throws Exception {
        Field field = AuthService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private void clearStaticMap(String fieldName) throws Exception {
        staticMap(fieldName).clear();
    }

    private int throttleSentInWindow(String fieldName, String email) throws Exception {
        Object state = staticMap(fieldName).get(email);
        Field sentInWindowField = state.getClass().getDeclaredField("sentInWindow");
        sentInWindowField.setAccessible(true);
        return sentInWindowField.getInt(state);
    }

    private void putThrottleState(
            String fieldName,
            String email,
            Instant windowStartedAt,
            Instant lastSentAt,
            int sentInWindow
    ) throws Exception {
        Class<?> stateClass = Class.forName("com.sep.comiverse.service.AuthService$OtpThrottleState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(Instant.class, Instant.class, int.class);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(windowStartedAt, lastSentAt, sentInWindow);
        staticMap(fieldName).put(email, state);
    }
}