package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.AuthorLicenseResponse;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.service.CloudinaryStorageService;
import com.sep.comiverse.service.CloudinaryUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorLicenseServiceTest {

    @Mock private IAuthorRepository authorRepository;
    @Mock private IUserRepository userRepository;
    @Mock private CloudinaryStorageService cloudinaryStorageService;

    private AuthorLicenseService service;

    @BeforeEach
    void setUp() {
        service = new AuthorLicenseService(authorRepository, userRepository, cloudinaryStorageService);
        lenient().when(authorRepository.save(any(AuthorEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initializePendingLicenseAuthor_createsProfileWithSevenDayDeadline() {
        UserEntity user = user("author@example.com");
        UUID adminId = UUID.randomUUID();
        UserEntity admin = user("admin@example.com");
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        Instant before = Instant.now();

        AuthorEntity author = service.initializePendingLicenseAuthor(user, adminId);

        assertEquals(AuthorLicenseStatus.PENDING_LICENSE, author.getLicenseStatus());
        assertSame(user, author.getUser());
        assertSame(admin, author.getCreatedByAdmin());
        assertEquals("Author Test", author.getDisplayName());
        assertTrue(author.getLicenseDeadlineAt().isAfter(before.plus(6, ChronoUnit.DAYS)));
        assertTrue(author.getLicenseDeadlineAt().isBefore(before.plus(8, ChronoUnit.DAYS)));
        verify(authorRepository).save(author);
    }

    @Test
    void initializePendingLicenseAuthor_returnsExistingProfileWithoutCreatingDuplicate() {
        UserEntity user = user("author@example.com");
        AuthorEntity existing = author(user, AuthorLicenseStatus.ACTIVE);
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(existing));

        assertSame(existing, service.initializePendingLicenseAuthor(user, null));
        verify(authorRepository, never()).save(any());
    }

    @Test
    void initializePendingLicenseAuthor_rejectsMissingUserIdentity() {
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.initializePendingLicenseAuthor(null, null)).getCode());
        UserEntity noId = UserEntity.builder().email("a@b.com").build();
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.initializePendingLicenseAuthor(noId, null)).getCode());
    }

    @Test
    void uploadLicense_validPdf_movesToPendingVerificationAndStoresSafeFilename() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.PENDING_LICENSE);
        author.setLicenseDeadlineAt(Instant.now().plus(2, ChronoUnit.DAYS));
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));
        when(cloudinaryStorageService.uploadRawFile(any(), anyString(), anyString()))
                .thenReturn(CloudinaryUploadResult.builder()
                        .secureUrl("https://cdn.test/license.pdf")
                        .publicId("license")
                        .build());
        MockMultipartFile file = pdf("C:\\fakepath\\license.pdf", "%PDF-1.7\nbody".getBytes());

        AuthorLicenseResponse response = service.uploadLicense(user.getId(), file);

        assertEquals(AuthorLicenseStatus.PENDING_VERIFICATION, author.getLicenseStatus());
        assertEquals("https://cdn.test/license.pdf", author.getLicenseUrl());
        assertEquals("license.pdf", author.getLicenseOriginalFilename());
        assertNotNull(author.getLicenseUploadedAt());
        assertEquals(AuthorLicenseStatus.PENDING_VERIFICATION, response.getStatus());
        assertFalse(response.isCanUploadLicense());
        verify(cloudinaryStorageService).uploadRawFile(
                any(), eq("C:\\fakepath\\license.pdf"), contains(author.getId().toString()));
    }

    @Test
    void uploadLicense_rejectsInvalidFileShapesAndFakePdfSignature() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.PENDING_LICENSE);
        author.setLicenseDeadlineAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadLicense(user.getId(), null)).getCode());

        MockMultipartFile wrongExt = new MockMultipartFile(
                "file", "license.txt", "application/pdf", "%PDF-".getBytes());
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadLicense(user.getId(), wrongExt)).getCode());

        MockMultipartFile wrongMime = new MockMultipartFile(
                "file", "license.pdf", "text/plain", "%PDF-".getBytes());
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadLicense(user.getId(), wrongMime)).getCode());

        MockMultipartFile fake = pdf("license.pdf", "not-pdf".getBytes());
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadLicense(user.getId(), fake)).getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    @Test
    void uploadLicense_expiredDeadline_marksExpiredAndRejects() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.PENDING_LICENSE);
        author.setLicenseDeadlineAt(Instant.now().minusSeconds(1));
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));

        CustomException error = assertThrows(CustomException.class,
                () -> service.uploadLicense(user.getId(), pdf("license.pdf", "%PDF-1.7".getBytes())));

        assertEquals(409, error.getCode());
        assertEquals(AuthorLicenseStatus.EXPIRED, author.getLicenseStatus());
        verify(authorRepository).save(author);
    }

    @Test
    void uploadLicense_rejectsStatusesThatCannotUpload() {
        UserEntity user = user("author@example.com");
        for (AuthorLicenseStatus status : List.of(
                AuthorLicenseStatus.PENDING_VERIFICATION,
                AuthorLicenseStatus.ACTIVE,
                AuthorLicenseStatus.EXPIRED,
                AuthorLicenseStatus.AUTHOR_DISABLED)) {
            AuthorEntity author = author(user, status);
            when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));
            assertEquals(409, assertThrows(CustomException.class,
                    () -> service.uploadLicense(user.getId(), pdf("license.pdf", "%PDF-1.7".getBytes())))
                    .getCode());
        }
    }

    @Test
    void approve_pendingVerificationWithPdf_activatesPublishingAndPayout() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        author.setLicenseUrl("https://cdn/license.pdf");
        UUID reviewer = UUID.randomUUID();
        when(authorRepository.findByIdAndDeletedFalse(author.getId())).thenReturn(Optional.of(author));

        AuthorLicenseResponse response = service.approve(author.getId(), reviewer);

        assertEquals(AuthorLicenseStatus.ACTIVE, author.getLicenseStatus());
        assertNotNull(author.getLicenseVerifiedAt());
        assertEquals(reviewer, author.getLicenseReviewedById());
        assertTrue(response.isCanPublishComic());
        assertTrue(response.isCanRequestAuthorPayout());
    }

    @Test
    void approve_rejectsUserEntityIdAndDoesNotFallbackToUserLookup() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        author.setLicenseUrl("https://cdn/license.pdf");
        UUID reviewer = UUID.randomUUID();

        // The supplied id is the UserEntity.id, not AuthorEntity.id.
        when(authorRepository.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> service.approve(user.getId(), reviewer));

        assertEquals(404, error.getCode());
        assertTrue(error.getMessage().contains("AuthorEntity.id"));
        verify(authorRepository).findByIdAndDeletedFalse(user.getId());
        verify(authorRepository, never()).findByUserIdAndDeletedFalse(user.getId());
        verify(authorRepository, never()).save(author);
        assertEquals(AuthorLicenseStatus.PENDING_VERIFICATION, author.getLicenseStatus());
    }

    @Test
    void approve_requiresPendingVerificationAndUploadedPdf() {
        UserEntity user = user("author@example.com");
        AuthorEntity active = author(user, AuthorLicenseStatus.ACTIVE);
        when(authorRepository.findByIdAndDeletedFalse(active.getId())).thenReturn(Optional.of(active));
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.approve(active.getId(), UUID.randomUUID())).getCode());

        AuthorEntity pending = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        when(authorRepository.findByIdAndDeletedFalse(pending.getId())).thenReturn(Optional.of(pending));
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.approve(pending.getId(), UUID.randomUUID())).getCode());
    }

    @Test
    void reject_setsNewDeadlineAndTrimsReasonUsingDefaultDeadline() {
        UserEntity user = user("author@example.com");
        AuthorEntity pending = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        when(authorRepository.findByIdAndDeletedFalse(pending.getId())).thenReturn(Optional.of(pending));
        Instant before = Instant.now();

        AuthorLicenseResponse response = service.reject(pending.getId(), UUID.randomUUID(), "  blurry scan  ", null);

        assertEquals(AuthorLicenseStatus.REJECTED, response.getStatus());
        assertEquals("blurry scan", pending.getLicenseRejectionReason());
        assertTrue(pending.getLicenseDeadlineAt().isAfter(before.plus(6, ChronoUnit.DAYS)));
        assertTrue(pending.getLicenseDeadlineAt().isBefore(before.plus(8, ChronoUnit.DAYS)));
    }

    @ParameterizedTest
    @CsvSource({
            "0,false",
            "1,true",
            "2,true",
            "29,true",
            "30,true",
            "31,false"
    })
    void reject_deadlineDaysBoundary_coversMinAndMaxEdges(int deadlineDays, boolean valid) {
        UserEntity user = user("author@example.com");
        AuthorEntity pending = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        when(authorRepository.findByIdAndDeletedFalse(pending.getId())).thenReturn(Optional.of(pending));
        Instant before = Instant.now();

        if (!valid) {
            CustomException error = assertThrows(CustomException.class, () ->
                    service.reject(pending.getId(), UUID.randomUUID(), "reason", deadlineDays));
            assertEquals(400, error.getCode());
            assertEquals(AuthorLicenseStatus.PENDING_VERIFICATION, pending.getLicenseStatus());
            return;
        }

        AuthorLicenseResponse response =
                service.reject(pending.getId(), UUID.randomUUID(), "reason", deadlineDays);
        assertEquals(AuthorLicenseStatus.REJECTED, response.getStatus());
        assertTrue(pending.getLicenseDeadlineAt().isAfter(before.plus(deadlineDays - 1L, ChronoUnit.DAYS)));
        assertTrue(pending.getLicenseDeadlineAt().isBefore(before.plus(deadlineDays + 1L, ChronoUnit.DAYS)));
    }

    @Test
    void reopen_onlyExpiredOrDisabled_returnsPendingLicense() {
        UserEntity user = user("author@example.com");
        AuthorEntity expired = author(user, AuthorLicenseStatus.EXPIRED);
        when(authorRepository.findByIdAndDeletedFalse(expired.getId()))
                .thenReturn(Optional.of(expired));
        AuthorLicenseResponse response =
                service.reopen(expired.getId(), UUID.randomUUID(), 7);
        assertEquals(AuthorLicenseStatus.PENDING_LICENSE, response.getStatus());
        assertTrue(response.isCanUploadLicense());
        AuthorEntity disabled = author(user, AuthorLicenseStatus.AUTHOR_DISABLED);
        when(authorRepository.findByIdAndDeletedFalse(disabled.getId())).thenReturn(Optional.of(disabled));
        AuthorLicenseResponse disabledResponse =
                service.reopen(disabled.getId(), UUID.randomUUID(), 7);
        assertEquals(AuthorLicenseStatus.PENDING_LICENSE, disabledResponse.getStatus());
        assertTrue(disabledResponse.isCanUploadLicense());

        AuthorEntity active = author(user, AuthorLicenseStatus.ACTIVE);
        when(authorRepository.findByIdAndDeletedFalse(active.getId())).thenReturn(Optional.of(active));
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.reopen(active.getId(), UUID.randomUUID(), 7)).getCode());
    }

    @Test
    void disable_blocksPublishingAndPayoutUntilReopenedAndApproved() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.ACTIVE);
        when(authorRepository.findByIdAndDeletedFalse(author.getId())).thenReturn(Optional.of(author));
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));

        service.disable(author.getId(), UUID.randomUUID());

        assertEquals(AuthorLicenseStatus.AUTHOR_DISABLED, author.getLicenseStatus());
        assertEquals(403, assertThrows(CustomException.class,
                () -> service.assertPublishingAllowed(user.getId())).getCode());
        assertEquals(403, assertThrows(CustomException.class,
                () -> service.assertAuthorPayoutAllowed(user.getId())).getCode());
        assertFalse(service.isAuthorPayoutAllowed(user.getId()));
    }

    @Test
    void activeAuthors_areAllowedButMissingLicenseStatusFailsClosed() {
        UserEntity user = user("author@example.com");
        AuthorEntity active = author(user, AuthorLicenseStatus.ACTIVE);
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(active));
        assertDoesNotThrow(() -> service.assertPublishingAllowed(user.getId()));
        assertDoesNotThrow(() -> service.assertAuthorPayoutAllowed(user.getId()));
        assertTrue(service.isAuthorPayoutAllowed(user.getId()));

        active.setLicenseStatus(null);
        active.setLicenseDeadlineAt(null);
        assertEquals(AuthorLicenseStatus.PENDING_LICENSE, service.effectiveStatus(active));
        assertEquals(403, assertThrows(CustomException.class,
                () -> service.assertPublishingAllowed(user.getId())).getCode());
        assertEquals(AuthorLicenseStatus.PENDING_LICENSE, active.getLicenseStatus());
        assertNotNull(active.getLicenseDeadlineAt());
    }

    @Test
    void reject_requiresNonBlankReason_evenWhenServiceCalledDirectly() {
        UserEntity user = user("author@example.com");
        AuthorEntity pending = author(user, AuthorLicenseStatus.PENDING_VERIFICATION);
        when(authorRepository.findByIdAndDeletedFalse(pending.getId())).thenReturn(Optional.of(pending));

        CustomException blank = assertThrows(CustomException.class,
                () -> service.reject(pending.getId(), UUID.randomUUID(), "   ", 7));

        assertEquals(400, blank.getCode());
        assertEquals("Rejection reason is required", blank.getMessage());
        assertEquals(AuthorLicenseStatus.PENDING_VERIFICATION, pending.getLicenseStatus());
    }

    @Test
    void expireOverdueLicenses_marksEveryCandidateExpiredInBatch() {
        AuthorEntity first = author(user("a@a.com"), AuthorLicenseStatus.PENDING_LICENSE);
        AuthorEntity second = author(user("b@b.com"), AuthorLicenseStatus.REJECTED);
        when(authorRepository.findExpiredLicenseCandidates(anyList(), any(Instant.class)))
                .thenReturn(List.of(first, second));

        int count = service.expireOverdueLicenses();

        assertEquals(2, count);
        assertEquals(AuthorLicenseStatus.EXPIRED, first.getLicenseStatus());
        assertEquals(AuthorLicenseStatus.EXPIRED, second.getLicenseStatus());
        verify(authorRepository).saveAll(List.of(first, second));
    }

    @Test
    void getMyLicense_refreshesExpiredStatusAndReturnsUploadFlags() {
        UserEntity user = user("author@example.com");
        AuthorEntity author = author(user, AuthorLicenseStatus.REJECTED);
        author.setLicenseDeadlineAt(Instant.now().minusSeconds(1));
        when(authorRepository.findByUserIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(author));

        AuthorLicenseResponse response = service.getMyLicense(user.getId());

        assertEquals(AuthorLicenseStatus.EXPIRED, response.getStatus());
        assertFalse(response.isCanUploadLicense());
        verify(authorRepository).save(author);
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    private UserEntity user(String email) {
        UserEntity user = UserEntity.builder()
                .username(email.substring(0, email.indexOf('@')))
                .fullName("Author Test")
                .email(email)
                .avatarUrl("avatar.png")
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private AuthorEntity author(UserEntity user, AuthorLicenseStatus status) {
        AuthorEntity author = AuthorEntity.builder()
                .user(user)
                .displayName("Author Test")
                .licenseStatus(status)
                .build();
        author.setId(UUID.randomUUID());
        author.setDeleted(false);
        return author;
    }
}
