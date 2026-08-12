package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AuthorProfileRequest;
import com.sep.comiverse.dto.response.AuthorProfileResponse;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AuthorType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.service.AuthorProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
class AuthorProfileServiceTest {

    @Mock
    private IAuthorRepository authorRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private AuthorLicenseService authorLicenseService;

    private AuthorProfileService service;

    @BeforeEach
    void setUp() {
        service = new AuthorProfileService(authorRepository, userRepository, authorLicenseService);
    }

    @Test
    void getMyProfileReturnsExistingProfileWithoutCreatingAnotherOne() {
        UUID userId = UUID.randomUUID();
        AuthorEntity author = author(userId);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(author));

        AuthorProfileResponse response = service.getMyProfile(userId);

        assertEquals(author.getId(), response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("Author Pen Name", response.getDisplayName());
        verify(authorRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getMyProfileCreatesDefaultsFromTheUserWhenMissing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(invocation -> {
            AuthorEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AuthorProfileResponse response = service.getMyProfile(userId);

        assertEquals("Author Full Name", response.getDisplayName());
        assertEquals("Author Full Name", response.getLegalName());
        assertEquals("author@example.com", response.getContactEmail());
        assertEquals("https://cdn.example/avatar.jpg", response.getAvatarUrl());
        assertEquals(AuthorType.INDIVIDUAL, response.getAuthorType());
        verify(authorRepository).save(any(AuthorEntity.class));
    }

    @Test
    void getMyProfileRequiresAuthenticatedUserId() {
        CustomException error = assertThrows(CustomException.class, () -> service.getMyProfile(null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
        verify(authorRepository, never()).findByUserIdAndDeletedFalse(any());
    }

    @Test
    void getMyProfileReturnsNotFoundWhenUserRecordDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class, () -> service.getMyProfile(userId));

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        assertEquals("User not found", error.getMessage());
        verify(authorRepository, never()).save(any());
    }

    @Test
    void updateMyProfileTrimsValuesUsesDefaultsAndFallsBackToAccountEmail() {
        UUID userId = UUID.randomUUID();
        AuthorEntity author = author(userId);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(author));
        when(authorRepository.save(author)).thenReturn(author);
        AuthorProfileRequest request = AuthorProfileRequest.builder()
                .authorType(null)
                .displayName("  Updated Pen Name  ")
                .legalName("   ")
                .bio("  Updated biography  ")
                .avatarUrl("  https://cdn.example/new-avatar.jpg  ")
                .contactEmail("   ")
                .externalProfileRef("  EXT-42  ")
                .note("   ")
                .build();

        AuthorProfileResponse response = service.updateMyProfile(userId, request);

        assertEquals(AuthorType.INDIVIDUAL, author.getAuthorType());
        assertEquals("Updated Pen Name", author.getDisplayName());
        assertNull(author.getLegalName());
        assertEquals("Updated biography", author.getBio());
        assertEquals("https://cdn.example/new-avatar.jpg", author.getAvatarUrl());
        assertEquals("author@example.com", author.getContactEmail());
        assertEquals("EXT-42", author.getExternalProfileRef());
        assertNull(author.getNote());
        assertSame(author.getId(), response.getId());
        verify(authorRepository).save(author);
    }

    @Test
    void updateMyProfileRejectsMissingPayloadAndBlankDisplayName() {
        UUID userId = UUID.randomUUID();
        CustomException missing = assertThrows(
                CustomException.class,
                () -> service.updateMyProfile(userId, null)
        );
        assertEquals("Author profile payload is required", missing.getMessage());

        AuthorEntity author = author(userId);
        when(authorRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(author));
        AuthorProfileRequest blankName = AuthorProfileRequest.builder().displayName("  ").build();

        CustomException blank = assertThrows(
                CustomException.class,
                () -> service.updateMyProfile(userId, blankName)
        );

        assertEquals("Display name is required", blank.getMessage());
        verify(authorRepository, never()).save(any());
    }

    private AuthorEntity author(UUID userId) {
        AuthorEntity author = AuthorEntity.builder()
                .user(user(userId))
                .authorType(AuthorType.INDIVIDUAL)
                .displayName("Author Pen Name")
                .legalName("Author Full Name")
                .contactEmail("contact@example.com")
                .build();
        author.setId(UUID.randomUUID());
        return author;
    }

    private UserEntity user(UUID userId) {
        UserEntity user = UserEntity.builder()
                .username("author")
                .fullName("Author Full Name")
                .email("author@example.com")
                .avatarUrl("https://cdn.example/avatar.jpg")
                .build();
        user.setId(userId);
        return user;
    }
}