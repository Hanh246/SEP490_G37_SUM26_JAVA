package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AdminUpdateUserRequest;
import com.sep.comiverse.dto.response.AdminUserResponse;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IRoleRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AdminUserService;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.util.EmailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private IUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private IRoleRepository roleRepository;
    @Mock
    private IAuthorRepository authorRepository;
    @Mock
    private AuthorLicenseService authorLicenseService;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                passwordEncoder,
                emailUtil,
                roleRepository,
                authorRepository,
                authorLicenseService
        );
    }

    @Test
    void banUserRejectsAdminAccounts() {
        UUID userId = UUID.randomUUID();
        UserEntity admin = user(userId, "ADMIN", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        CustomException error = assertThrows(CustomException.class, () -> service.banUser(userId));

        assertEquals("Cannot ban an Admin account", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void banUserPersistsInactiveStatus() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        AdminUserResponse response = service.banUser(userId);

        assertEquals("INACTIVE", reader.getStatus());
        assertEquals("Banned", response.getStatus());
        verify(userRepository).save(reader);
    }

    @Test
    void banUserRejectsAnAlreadyInactiveAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "INACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        CustomException error = assertThrows(CustomException.class, () -> service.banUser(userId));

        assertEquals("User is already banned", error.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void banUserRejectsPendingVerificationAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "PENDING_VERIFICATION");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        CustomException error = assertThrows(CustomException.class, () -> service.banUser(userId));

        assertEquals("Cannot ban an account pending email verification", error.getMessage());
        assertEquals("PENDING_VERIFICATION", reader.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unbanUserRestoresActiveStatus() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "INACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        AdminUserResponse response = service.unbanUser(userId);

        assertEquals("ACTIVE", reader.getStatus());
        assertEquals("Active", response.getStatus());
        verify(userRepository).save(reader);
    }

    @Test
    void unbanUserCannotActivatePendingVerificationAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "PENDING_VERIFICATION");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        CustomException error = assertThrows(CustomException.class, () -> service.unbanUser(userId));

        assertEquals("Only banned accounts can be unbanned", error.getMessage());
        assertEquals("PENDING_VERIFICATION", reader.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetUserPasswordToDefaultUsesTheProjectDefaultAndConsumesResetToken() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "ACTIVE");
        reader.setResetToken("old-token");
        reader.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(passwordEncoder.encode("abcd1234")).thenReturn("encoded-default-password");

        service.resetUserPasswordToDefault(userId);

        assertEquals("encoded-default-password", reader.getPassword());
        assertNull(reader.getResetToken());
        assertNull(reader.getResetTokenExpiresAt());
        verify(passwordEncoder).encode("abcd1234");
        verify(userRepository).save(reader);
    }

    @Test
    void updateUserTrimsNameChangesRoleAndPersistsLanguages() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "ACTIVE");
        RoleEntity translator = RoleEntity.builder().roleName("TRANSLATOR").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(roleRepository.findByRoleName("TRANSLATOR")).thenReturn(Optional.of(translator));
        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "  Updated User  ",
                " translator ",
                List.of("Vietnamese", "English")
        );

        AdminUserResponse response = service.updateUser(userId, request);

        assertEquals("Updated User", reader.getFullName());
        assertSame(translator, reader.getRole());
        assertEquals("Vietnamese,English", reader.getAssignedLanguages());
        assertEquals("Translator", response.getRole());
        assertEquals(List.of("Vietnamese", "English"), response.getAssignedLanguages());
        verify(userRepository).save(reader);
    }

    @Test
    void updateUserRejectsUnknownRoleWithoutSaving() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(roleRepository.findByRoleName("UNKNOWN")).thenReturn(Optional.empty());
        AdminUpdateUserRequest request = new AdminUpdateUserRequest("Reader One", "unknown", null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateUser(userId, request)
        );

        assertEquals("Role not found: UNKNOWN", error.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserToAuthorInitializesPendingLicenseFlowWhenAuthorProfileDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "ACTIVE");
        RoleEntity authorRole = RoleEntity.builder().roleName("AUTHOR").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(roleRepository.findByRoleName("AUTHOR")).thenReturn(Optional.of(authorRole));
        when(authorRepository.existsByUserIdAndDeletedFalse(userId)).thenReturn(false);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Reader One",
                "author",
                null
        );

        AdminUserResponse response = service.updateUser(userId, request);

        assertSame(authorRole, reader.getRole());
        assertEquals("Author", response.getRole());
        verify(authorLicenseService).initializePendingLicenseAuthor(reader, null);
        verify(userRepository).save(reader);
    }

    @Test
    void updateExistingAuthorDoesNotReinitializeLicenseFlow() {
        UUID userId = UUID.randomUUID();
        UserEntity author = user(userId, "AUTHOR", "ACTIVE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(authorRepository.existsByUserIdAndDeletedFalse(userId)).thenReturn(true);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Updated Author",
                null,
                null
        );

        service.updateUser(userId, request);

        verify(authorLicenseService, never()).initializePendingLicenseAuthor(any(), any());
        verify(userRepository).save(author);
    }

    @Test
    void getUserByIdMapsProjectLeaderAndLanguageListForTheAdminUi() {
        UUID userId = UUID.randomUUID();
        UserEntity leader = user(userId, "PROJECT_LEADER", "ACTIVE");
        leader.setAssignedLanguages("Vietnamese, English");
        when(userRepository.findById(userId)).thenReturn(Optional.of(leader));

        AdminUserResponse response = service.getUserById(userId);

        assertEquals("Project Leader", response.getRole());
        assertEquals(List.of("Vietnamese", "English"), response.getAssignedLanguages());
        assertEquals("USR-" + userId.toString().substring(0, 8).toUpperCase(), response.getUserId());
    }

    @Test
    void getUserByIdKeepsPendingVerificationDistinctFromActive() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER", "PENDING_VERIFICATION");
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));

        AdminUserResponse response = service.getUserById(userId);

        assertEquals("Pending Verification", response.getStatus());
    }

    @Test
    void getUserByIdReturnsNotFoundForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class, () -> service.getUserById(userId));

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        assertEquals("User not found", error.getMessage());
    }

    private UserEntity user(UUID id, String roleName, String status) {
        UserEntity user = UserEntity.builder()
                .username("reader")
                .password("old-password")
                .fullName("Reader One")
                .email("reader@example.com")
                .role(RoleEntity.builder().roleName(roleName).build())
                .provider("LOCAL")
                .status(status)
                .build();
        user.setId(id);
        return user;
    }
}
