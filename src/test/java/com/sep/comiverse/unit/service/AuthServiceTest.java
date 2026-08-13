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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    @Test
    void authenticateReturnsAnActiveUserForValidCredentials() {
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

        UserEntity result = service.authenticate("reader", "secret");

        assertSame(user, result);
    }

    @Test
    void authenticateDoesNotRevealWhetherTheAccountExistsOrPasswordIsWrong() {
        when(userRepository.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());

        CustomException missing = assertThrows(
                CustomException.class,
                () -> service.authenticate("missing", "secret")
        );

        UserEntity user = localUser("reader@example.com", "ACTIVE");
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        CustomException wrongPassword = assertThrows(
                CustomException.class,
                () -> service.authenticate("reader", "wrong")
        );

        assertEquals("Invalid username or password", missing.getMessage());
        assertEquals(missing.getMessage(), wrongPassword.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getHttpStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "PENDING_VERIFICATION"})
    void authenticateRejectsAccountsThatCannotSignIn(String status) {
        UserEntity user = localUser("reader@example.com", status);
        when(userRepository.findByUsernameOrEmail("reader", "reader")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.authenticate("reader", "secret")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getHttpStatus());
    }

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
    void verifyEmailActivatesPendingLocalAccountAndConsumesOtp() {
        String otp = "123456";
        UserEntity user = localUser("reader@example.com", "PENDING_VERIFICATION");
        user.setEmailVerificationToken(sha256(otp));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        service.verifyEmail(" Reader@Example.com ", " 123456 ");

        assertEquals("ACTIVE", user.getStatus());
        assertNull(user.getEmailVerificationToken());
        assertNull(user.getEmailVerificationExpiresAt());
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
    void forgotPasswordReturnsTheSameWayForAnUnknownEmailWithoutSendingMail() {
        String email = "missing-" + UUID.randomUUID() + "@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.forgotPassword(email);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailUtil);
    }

    @Test
    void forgotPasswordStoresOnlyTheOtpHashForALocalAccount() {
        String email = "reader-" + UUID.randomUUID() + "@example.com";
        UserEntity user = localUser(email, "ACTIVE");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        service.forgotPassword("  " + email.toUpperCase() + "  ");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailUtil).sendOTP(eq(email), otpCaptor.capture(), eq("Reader One"));
        verify(userRepository).save(user);
        assertTrue(otpCaptor.getValue().matches("\\d{6}"));
        assertEquals(sha256(otpCaptor.getValue()), user.getResetToken());
        assertFalse(user.getResetToken().equals(otpCaptor.getValue()));
        assertTrue(user.getResetTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(4)));
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
    void updateProfileTrimsEditableFieldsAndClearsBlankBio() {
        UUID userId = UUID.randomUUID();
        UserEntity user = localUser("reader@example.com", "ACTIVE");
        user.setFullName("Old Name");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        LocalDate birthDate = LocalDate.of(2001, 5, 20);

        UserEntity result = service.updateProfile(
                userId,
                "  New Name  ",
                "https://cdn.example/avatar.jpg",
                "https://cdn.example/background.jpg",
                birthDate,
                "   "
        );

        assertSame(user, result);
        assertEquals("New Name", user.getFullName());
        assertEquals("https://cdn.example/avatar.jpg", user.getAvatarUrl());
        assertEquals("https://cdn.example/background.jpg", user.getBackgroundImageUrl());
        assertEquals(birthDate, user.getDateOfBirth());
        assertNull(user.getBio());
        verify(userRepository).save(user);
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
    private void clearStaticMap(String fieldName) throws Exception {
        Field field = AuthService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map<Object, Object>) field.get(null)).clear();
    }
}