package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorComicService;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.service.ChapterPremiumPolicyService;
import com.sep.comiverse.service.CloudinaryStorageService;
import com.sep.comiverse.service.CloudinaryUploadResult;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorChapterServiceTest {

    private static final long TEN_MB = 10L * 1024L * 1024L;

    @Mock private AuthorComicService authorComicService;
    @Mock private IComicRepository comicRepository;
    @Mock private IChapterRepository chapterRepository;
    @Mock private ISubmissionRepository submissionRepository;
    @Mock private IReadingHistoryRepository readingHistoryRepository;
    @Mock private ITeamTaskRepository teamTaskRepository;
    @Mock private ChapterCrudPlugin chapterCrudPlugin;
    @Mock private ComicCrudPlugin comicCrudPlugin;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private CloudinaryStorageService cloudinaryStorageService;
    @Mock private NotificationService notificationService;
    @Mock private ChapterPremiumPolicyService chapterPremiumPolicyService;
    @Mock private AuthorLicenseService authorLicenseService;

    private AuthorChapterService service;

    @BeforeEach
    void setUp() {
        service = new AuthorChapterService(
                authorComicService,
                comicRepository,
                chapterRepository,
                submissionRepository,
                readingHistoryRepository,
                teamTaskRepository,
                chapterCrudPlugin,
                comicCrudPlugin,
                redisTemplate,
                cloudinaryStorageService,
                notificationService,
                chapterPremiumPolicyService,
                authorLicenseService
        );

        ReflectionTestUtils.setField(service, "maxPages", 200);
        ReflectionTestUtils.setField(service, "maxTotalUploadSizeBytes", 100L * 1024L * 1024L);

        lenient().when(chapterRepository.save(any(ChapterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(submissionRepository.save(any(SubmissionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chapterRepository.findAllByComic_IdAndDeletedFalseAndModerationStatus(
                        any(), eq(ChapterStatus.PUBLISHED)))
                .thenReturn(List.of());
    }

    // ===== uploadChapterFolder: request / identity =====

    @Test
    void uploadChapterFolderRejectsNullRequest() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(UUID.randomUUID(), null, List.of(), List.of())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(authorLicenseService, authorComicService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderRejectsMissingAuthorIdBeforeLicenseCheck() {
        UUID comicId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(null, "1", "Title");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(authorLicenseService, authorComicService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderRejectsMissingChapterNumber() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "   ", "Title");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(authorLicenseService, authorComicService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderRejectsInvalidChapterNumberFormat() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "0", "Title");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(authorLicenseService, authorComicService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderDoesNotRequireAuthorLicenseAndContinuesNormalValidation() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");
        ComicEntity comic = comic(comicId, authorId);

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("at least one image"));
        verify(authorComicService).getOwnedComic(comicId, authorId);
        verifyNoInteractions(authorLicenseService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderRejectsDuplicateNormalizedChapterNumber() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1,5", "Title");

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic(comicId, authorId));
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "1.5"))
                .thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    // ===== uploadChapterFolder: folder/input-domain validation =====

    @Test
    void uploadChapterFolderRejectsEmptyFiles() {
        assertFolderValidationError(List.of(), List.of(), 400);
    }

    @Test
    void uploadChapterFolderRejectsMissingRelativePaths() {
        assertFolderValidationError(List.of(png("01.png")), List.of(), 400);
    }

    @Test
    void uploadChapterFolderRejectsMismatchedFileAndPathCounts() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("Chapter 1/01.png", "Chapter 1/02.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsNullImageFile() {
        assertFolderValidationError(
                Collections.singletonList(null),
                List.of("Chapter 1/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsEmptyImageFile() {
        MockMultipartFile empty =
                new MockMultipartFile("file", "01.png", "image/png", new byte[0]);

        assertFolderValidationError(
                List.of(empty),
                List.of("Chapter 1/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsBlankRelativePath() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("   "),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsAbsolutePath() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("/Chapter 1/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsUnixPathTraversal() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("../01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsWindowsPathTraversal() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("..\\01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsHiddenPath() {
        assertFolderValidationError(
                List.of(png(".01.png")),
                List.of("Chapter 1/.01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsMacOsMetadataFolder() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("__MACOSX/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsNestedFolderStructure() {
        assertFolderValidationError(
                List.of(png("01.png")),
                List.of("Chapter 1/sub/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsFilesFromMultipleChapterFolders() {
        assertFolderValidationError(
                List.of(png("01.png"), png("02.png")),
                List.of("Chapter 1/01.png", "Chapter 2/02.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsUploadedFilenameMismatch() {
        assertFolderValidationError(
                List.of(png("02.png")),
                List.of("Chapter 1/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsUnsupportedImageExtension() {
        MockMultipartFile txt =
                new MockMultipartFile("file", "01.txt", "text/plain", "x".getBytes());

        assertFolderValidationError(
                List.of(txt),
                List.of("Chapter 1/01.txt"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsUnreadableImage() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("01.png");
        when(file.getSize()).thenReturn(100L);
        when(file.getBytes()).thenThrow(new IOException("read failed"));

        assertFolderValidationError(
                List.of(file),
                List.of("Chapter 1/01.png"),
                400
        );
    }

    @Test
    void uploadChapterFolderRejectsInvalidImageBytes() {
        MockMultipartFile fake =
                new MockMultipartFile("file", "01.png", "image/png", "not-an-image".getBytes());

        assertFolderValidationError(
                List.of(fake),
                List.of("Chapter 1/01.png"),
                400
        );
    }

    // ===== page-count BVA =====

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 5})
    void uploadChapterFolderAcceptsPageCountWithinConfiguredBoundary(int pageCount) {
        ReflectionTestUtils.setField(service, "maxPages", 5);

        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request =
                new ChapterUploadRequest(authorId, "1", "Boundary chapter");

        stubUploadOwnership(comicId, authorId, "1");
        stubCloudinaryUpload();

        List<MultipartFile> files = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (int index = 1; index <= pageCount; index++) {
            String name = String.format("%03d.png", index);
            files.add(png(name));
            paths.add("Chapter 1/" + name);
        }

        var response = service.uploadChapterFolder(comicId, request, files, paths);

        assertEquals(pageCount, response.getPageCount());
        verify(cloudinaryStorageService, times(pageCount))
                .uploadImage(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void uploadChapterFolderRejectsPageCountOutsideConfiguredBoundary(int pageCount) {
        ReflectionTestUtils.setField(service, "maxPages", 5);

        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request =
                new ChapterUploadRequest(authorId, "1", "Boundary chapter");

        stubUploadOwnership(comicId, authorId, "1");

        List<MultipartFile> files = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (int index = 1; index <= pageCount; index++) {
            String name = String.format("%03d.png", index);
            files.add(png(name));
            paths.add("Chapter 1/" + name);
        }

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, files, paths)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    // ===== per-image size BVA =====

    @Test
    void uploadChapterFolderAcceptsImageOneByteBelowTenMbLimit() throws Exception {
        assertUploadAcceptedAtReportedImageSize(TEN_MB - 1);
    }

    @Test
    void uploadChapterFolderAcceptsImageAtTenMbLimit() throws Exception {
        assertUploadAcceptedAtReportedImageSize(TEN_MB);
    }

    @Test
    void uploadChapterFolderRejectsImageOneByteAboveTenMbLimit() throws Exception {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");
        stubUploadOwnership(comicId, authorId, "1");

        MultipartFile file = mockValidPng("01.png", TEN_MB + 1);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(
                        comicId,
                        request,
                        List.of(file),
                        List.of("Chapter 1/01.png")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    // ===== total upload size BVA =====

    @Test
    void uploadChapterFolderAcceptsTotalSizeOneByteBelowConfiguredLimit() throws Exception {
        assertUploadAcceptedAtTotalLimit(19L, 20L);
    }

    @Test
    void uploadChapterFolderAcceptsTotalSizeAtConfiguredLimit() throws Exception {
        assertUploadAcceptedAtTotalLimit(20L, 20L);
    }

    @Test
    void uploadChapterFolderRejectsTotalSizeOneByteAboveConfiguredLimit() throws Exception {
        ReflectionTestUtils.setField(service, "maxTotalUploadSizeBytes", 20L);

        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");
        stubUploadOwnership(comicId, authorId, "1");

        MultipartFile file = mockValidPng("01.png", 21L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(
                        comicId,
                        request,
                        List.of(file),
                        List.of("Chapter 1/01.png")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    // ===== upload business rules / ordering =====

    @Test
    void uploadChapterFolderRejectsContentPreviouslyRejectedByModeration() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");
        when(chapterRepository.existsByContentHashAndModerationStatus(
                anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(
                        comicId,
                        request,
                        List.of(png("01.png")),
                        List.of("Chapter 1/01.png")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolderNaturallyOrdersPageImagesBeforeUpload() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");
        stubCloudinaryUpload();

        service.uploadChapterFolder(
                comicId,
                request,
                List.of(png("10.png"), png("2.png"), png("1.png")),
                List.of(
                        "Chapter 1/10.png",
                        "Chapter 1/2.png",
                        "Chapter 1/1.png"
                )
        );

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(cloudinaryStorageService, times(3))
                .uploadImage(any(), nameCaptor.capture(), anyString());

        assertEquals(
                List.of("001-1.png", "002-2.png", "003-10.png"),
                nameCaptor.getAllValues()
        );
    }

    @Test
    void uploadChapterFolderMakesDuplicateDisplayNamesUnique() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");
        stubCloudinaryUpload();

        service.uploadChapterFolder(
                comicId,
                request,
                List.of(png("01.png"), png("01.png")),
                List.of("Chapter 1/01.png", "Chapter 1/01.png")
        );

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(cloudinaryStorageService, times(2))
                .uploadImage(any(), nameCaptor.capture(), anyString());

        List<String> uploadedNames = nameCaptor.getAllValues();
        assertEquals(2, uploadedNames.size());
        assertEquals(2, uploadedNames.stream().distinct().count());
        assertTrue(uploadedNames.stream().anyMatch(name -> name.endsWith("01.png")));
        assertTrue(uploadedNames.stream().anyMatch(name -> name.endsWith("01-duplicate-2.png")));
    }

    @Test
    void uploadChapterFolderHappyPathCreatesPreviewReadyChapter() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterUploadRequest request =
                new ChapterUploadRequest(authorId, "1,5", "  Chapter title  ");

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "1.5"))
                .thenReturn(false);
        when(chapterRepository.existsByContentHashAndModerationStatus(
                anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(false);
        when(chapterPremiumPolicyService.isPremiumChapter("1.5")).thenReturn(true);
        when(cloudinaryStorageService.uploadImage(
                any(), startsWith("001-"), contains("chapter-1.5")))
                .thenReturn(CloudinaryUploadResult.builder()
                        .secureUrl("https://cdn.test/001.png")
                        .build());

        var response = service.uploadChapterFolder(
                comicId,
                request,
                List.of(png("01.png")),
                List.of("Chapter 1.5/01.png")
        );

        assertEquals("1.5", response.getChapterNumber());
        assertEquals("Chapter title", response.getTitle());
        assertEquals(ChapterStatus.PREVIEW_READY, response.getStatus());
        assertEquals(1, response.getPageCount());
        assertEquals("https://cdn.test/001.png", response.getPages().get(0).getImageUrl());
        verify(comicRepository).save(comic);
    }

    // ===== previewChapter =====

    @Test
    void previewChapterRejectedChapterKeepsLivePagesAndFeedback() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ChapterEntity chapter = chapter(
                comic(comicId, authorId),
                chapterId,
                "2",
                ChapterStatus.REJECTED,
                List.of("https://cdn.test/ch2-001.png", "https://cdn.test/ch2-002.png")
        );
        chapter.setRejectionReason("Fix page 1 lettering");

        stubOwnedChapter(comicId, chapterId, authorId, chapter);

        var response = service.previewChapter(comicId, chapterId, authorId);

        assertEquals(ChapterStatus.REJECTED, response.getStatus());
        assertEquals("Fix page 1 lettering", response.getRejectionReason());
        assertEquals(2, response.getPageCount());
        assertEquals("https://cdn.test/ch2-001.png", response.getPages().get(0).getImageUrl());
    }

    @Test
    void previewChapterFallsBackToSubmissionEvidenceWhenLiveImagesAreMissing() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ChapterEntity chapter = chapter(
                comic(comicId, authorId),
                chapterId,
                "9",
                ChapterStatus.REJECTED,
                List.of()
        );
        chapter.setRejectionReason("Restore moderation evidence");
        chapter.setPageCount(2);

        SubmissionEntity evidence = SubmissionEntity.builder()
                .chapterId(chapterId)
                .queueType("author")
                .status("rejected")
                .chapterImages(List.of(
                        "https://cdn.test/9-001.png",
                        "https://cdn.test/9-002.png"
                ))
                .pageCount(2)
                .build();

        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(submissionRepository
                .findTopByChapterIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        chapterId, "author"))
                .thenReturn(Optional.of(evidence));

        var response = service.previewChapter(comicId, chapterId, authorId);

        assertEquals(2, response.getPageCount());
        assertEquals(2, response.getPages().size());
        assertEquals("https://cdn.test/9-001.png", response.getPages().get(0).getImageUrl());
        assertEquals("Restore moderation evidence", response.getRejectionReason());
    }

    @Test
    void previewChapterRejectsMissingComicId() {
        assertEquals(
                400,
                assertThrows(
                        CustomException.class,
                        () -> service.previewChapter(null, UUID.randomUUID(), UUID.randomUUID())
                ).getCode()
        );
    }

    @Test
    void previewChapterRejectsMissingChapterId() {
        assertEquals(
                400,
                assertThrows(
                        CustomException.class,
                        () -> service.previewChapter(UUID.randomUUID(), null, UUID.randomUUID())
                ).getCode()
        );
    }

    @Test
    void previewChapterRejectsMissingAuthorId() {
        assertEquals(
                400,
                assertThrows(
                        CustomException.class,
                        () -> service.previewChapter(UUID.randomUUID(), UUID.randomUUID(), null)
                ).getCode()
        );
    }

    @Test
    void previewChapterRejectsChapterNotOwnedByAuthor() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
                chapterId, comicId, authorId))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.previewChapter(comicId, chapterId, authorId)
        );

        assertEquals(404, error.getCode());
    }

    @Test
    void previewChapterDerivesSubmittedStatusFromLatestSubmissionWhenEntityStatusIsMissing() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ChapterEntity chapter = chapter(
                comic(comicId, authorId),
                chapterId,
                "3",
                null,
                List.of("https://cdn.test/3.png")
        );

        SubmissionEntity submission = SubmissionEntity.builder()
                .chapterId(chapterId)
                .authorId(authorId)
                .queueType("author")
                .status("pending")
                .build();

        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(submissionRepository
                .findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        chapterId, authorId, "author"))
                .thenReturn(Optional.of(submission));

        var response = service.previewChapter(comicId, chapterId, authorId);

        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, response.getStatus());
    }

    // ===== listChapters =====

    @Test
    void listChaptersUsesDefaultPaginationWhenRequestIsNull() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = chapter(
                comic,
                UUID.randomUUID(),
                "1",
                ChapterStatus.PUBLISHED,
                List.of("a")
        );

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(
                eq(comicId), eq(authorId), any()))
                .thenReturn(new PageImpl<>(List.of(chapter)));

        var result = service.listChapters(comicId, authorId, null);

        assertEquals(1, result.getTotalElements());
        assertEquals("1", result.getContent().get(0).getChapterNumber());
    }

    @Test
    void listChaptersUsesProvidedPagination() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        PaginationSearchDTO pagination = new PaginationSearchDTO();

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(
                eq(comicId), eq(authorId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() -> service.listChapters(comicId, authorId, pagination));

        verify(chapterRepository).findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(
                eq(comicId), eq(authorId), any());
    }

    // ===== submitForReview =====

    @Test
    void submitForReviewRejectsWhenComicIsNotPublished() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.DRAFT);

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)
        );

        assertEquals(409, error.getCode());
        assertEquals("Comic must be published before submitting chapters for review", error.getMessage());
        verifyNoInteractions(chapterRepository, submissionRepository);
        verifyNoInteractions(authorLicenseService);
    }

    @Test
    void submitForReviewDoesNotRequireAuthorLicense() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.PREVIEW_READY,
                List.of("a")
        );

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);

        assertDoesNotThrow(() -> service.submitForReview(comicId, chapterId, authorId));

        verifyNoInteractions(authorLicenseService);
        verify(submissionRepository).save(any(SubmissionEntity.class));
    }

    @Test
    void submitForReviewRejectsChapterWithoutImages() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.PREVIEW_READY,
                List.of()
        );

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)
        );

        assertEquals(400, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsAlreadySubmittedChapter() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.SUBMITTED_FOR_REVIEW,
                List.of("a")
        );

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsPublishedChapter() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.PUBLISHED,
                List.of("a")
        );

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsPreviouslyRejectedUnmodifiedContent() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.REJECTED,
                List.of("a")
        );
        chapter.setContentHash("same-hash");

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);
        when(chapterRepository.existsByContentHashAndModerationStatus(
                "same-hash", ChapterStatus.REJECTED))
                .thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewHappyPathCreatesSubmissionAndMovesChapterToSubmitted() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "2",
                ChapterStatus.PREVIEW_READY,
                List.of("a", "b")
        );

        stubSubmitOwnership(comicId, chapterId, authorId, comic, chapter);

        var response = service.submitForReview(comicId, chapterId, authorId);

        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, chapter.getModerationStatus());
        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, response.getStatus());

        verify(submissionRepository).save(argThat(submission ->
                "pending".equalsIgnoreCase(submission.getStatus())
                        && submission.getPageCount() == 2
                        && submission.getChapterImages().equals(List.of("a", "b"))
        ));

        verify(notificationService).notifyModeratorsWithLanguage(
                eq(comic.getLanguage()),
                contains("review"),
                contains("Chapter 2"),
                eq("UPDATE"),
                any()
        );
    }

    // ===== updateChapter =====

    @Test
    void updateChapterRejectsNullRequest() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateChapter(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void updateChapterRejectsDuplicateChapterNumber() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.PREVIEW_READY,
                List.of("a")
        );

        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "2"))
                .thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateChapter(
                        comicId,
                        chapterId,
                        authorId,
                        new ChapterUploadRequest(authorId, "2", null)
                )
        );

        assertEquals(409, error.getCode());
    }

    @Test
    void updateChapterChangesChapterNumberAndRecalculatesPremium() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.PREVIEW_READY);

        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(f.comicId, "2"))
                .thenReturn(false);
        when(chapterPremiumPolicyService.isPremiumChapter("2")).thenReturn(true);

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, "2", null)
        );

        assertEquals("2", f.chapter.getChapterNumber());
        assertTrue(Boolean.TRUE.equals(f.chapter.getIsPremium()));
    }

    @Test
    void updateChapterTrimsChangedTitle() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.PREVIEW_READY);
        f.chapter.setTitle("Old");

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "  New title  ")
        );

        assertEquals("New title", f.chapter.getTitle());
    }

    @Test
    void updateChapterNormalizesBlankTitleToNull() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.PREVIEW_READY);
        f.chapter.setTitle("Old");

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "   ")
        );

        assertNull(f.chapter.getTitle());
    }

    @Test
    void updateChapterSameMetadataKeepsCurrentModerationStatus() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.PUBLISHED);
        f.chapter.setTitle("Same");

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, "1", "Same")
        );

        assertEquals(ChapterStatus.PUBLISHED, f.chapter.getModerationStatus());
        verify(submissionRepository, never())
                .findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), anyString(), anyString());
    }

    @Test
    void updateChapterChangedMetadataWithImagesMovesToPreviewReady() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.PUBLISHED);
        f.chapter.setTitle("Old");

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "New")
        );

        assertEquals(ChapterStatus.PREVIEW_READY, f.chapter.getModerationStatus());
    }

    @Test
    void updateChapterChangedMetadataWithoutImagesMovesToDraft() {
        UpdateFixture f = updateFixture(List.of(), ChapterStatus.REJECTED);
        f.chapter.setTitle("Old");

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "New")
        );

        assertEquals(ChapterStatus.DRAFT, f.chapter.getModerationStatus());
    }

    @Test
    void updateChapterChangedMetadataClearsRejectionAudit() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.REJECTED);
        f.chapter.setTitle("Old");
        f.chapter.setRejectionReason("Fix");
        f.chapter.setRejectedById(UUID.randomUUID());

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "New")
        );

        assertNull(f.chapter.getRejectionReason());
        assertNull(f.chapter.getRejectedById());
    }

    @Test
    void updateChapterChangedMetadataCancelsPendingSubmission() {
        UpdateFixture f = updateFixture(List.of("a"), ChapterStatus.SUBMITTED_FOR_REVIEW);
        f.chapter.setTitle("Old");

        SubmissionEntity pending = SubmissionEntity.builder()
                .chapterId(f.chapterId)
                .authorId(f.authorId)
                .queueType("author")
                .status("pending")
                .build();
        pending.setDeleted(false);

        when(submissionRepository
                .findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        f.chapterId, f.authorId, "author", "pending"))
                .thenReturn(List.of(pending));

        service.updateChapter(
                f.comicId,
                f.chapterId,
                f.authorId,
                new ChapterUploadRequest(f.authorId, null, "New")
        );

        assertEquals("cancelled", pending.getStatus());
        assertTrue(Boolean.TRUE.equals(pending.getDeleted()));
        verify(submissionRepository).save(pending);
    }

    // ===== replaceChapterFolder =====

    @Test
    void replaceChapterFolderDoesNotRequireAuthorLicenseAndStillChecksOwnership() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
                chapterId, comicId, authorId
        )).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.replaceChapterFolder(
                        comicId,
                        chapterId,
                        authorId,
                        List.of(png("01.png")),
                        List.of("Chapter 1/01.png")
                )
        );

        assertEquals(404, error.getCode());
        verify(authorComicService).getOwnedComic(comicId, authorId);
        verify(chapterRepository).findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
                chapterId, comicId, authorId
        );
        verifyNoInteractions(authorLicenseService, cloudinaryStorageService);
    }

    @Test
    void replaceChapterFolderRejectsPreviouslyRejectedSameContent() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.REJECTED,
                List.of("old")
        );

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(chapterRepository.existsByContentHashAndModerationStatus(
                anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(true);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.replaceChapterFolder(
                        comicId,
                        chapterId,
                        authorId,
                        List.of(png("01.png")),
                        List.of("Chapter 1/01.png")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    @Test
    void replaceChapterFolderHappyPathReplacesImagesAndReturnsPreviewReady() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.REJECTED,
                List.of("old")
        );
        chapter.setRejectionReason("Fix");
        chapter.setRejectedById(UUID.randomUUID());

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(chapterRepository.existsByContentHashAndModerationStatus(
                anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(false);
        when(cloudinaryStorageService.uploadImage(any(), anyString(), anyString()))
                .thenReturn(CloudinaryUploadResult.builder()
                        .secureUrl("https://cdn.test/new.png")
                        .build());

        var response = service.replaceChapterFolder(
                comicId,
                chapterId,
                authorId,
                List.of(png("01.png")),
                List.of("Chapter 1/01.png")
        );

        assertEquals(ChapterStatus.PREVIEW_READY, chapter.getModerationStatus());
        assertEquals(List.of("https://cdn.test/new.png"), chapter.getImages());
        assertNotNull(chapter.getContentHash());
        assertNull(chapter.getRejectionReason());
        assertNull(chapter.getRejectedById());
        assertEquals(ChapterStatus.PREVIEW_READY, response.getStatus());
    }

    @Test
    void replaceChapterFolderCancelsPendingSubmission() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = chapter(
                comic,
                chapterId,
                "1",
                ChapterStatus.SUBMITTED_FOR_REVIEW,
                List.of("old")
        );

        SubmissionEntity pending = SubmissionEntity.builder()
                .chapterId(chapterId)
                .authorId(authorId)
                .queueType("author")
                .status("pending")
                .build();
        pending.setDeleted(false);

        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        stubOwnedChapter(comicId, chapterId, authorId, chapter);
        when(chapterRepository.existsByContentHashAndModerationStatus(
                anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(false);
        when(cloudinaryStorageService.uploadImage(any(), anyString(), anyString()))
                .thenReturn(CloudinaryUploadResult.builder()
                        .secureUrl("https://cdn.test/new.png")
                        .build());
        when(submissionRepository
                .findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        chapterId, authorId, "author", "pending"))
                .thenReturn(List.of(pending));

        service.replaceChapterFolder(
                comicId,
                chapterId,
                authorId,
                List.of(png("01.png")),
                List.of("Chapter 1/01.png")
        );

        assertEquals("cancelled", pending.getStatus());
        assertTrue(Boolean.TRUE.equals(pending.getDeleted()));
        verify(submissionRepository).save(pending);
    }

    // ===== deleteChapter =====

    @Test
    void deleteChapterHardDeletesDependenciesBeforeChapterRow() {
        DeleteFixture f = deleteFixture();

        service.deleteChapter(f.comicId, f.chapterId, f.authorId);

        InOrder order = inOrder(
                teamTaskRepository,
                readingHistoryRepository,
                submissionRepository,
                chapterRepository
        );
        order.verify(teamTaskRepository).hardDeleteAllByChapterId(f.chapterId);
        order.verify(readingHistoryRepository).hardDeleteAllByChapterId(f.chapterId);
        order.verify(submissionRepository).hardDeleteAllByChapterId(f.chapterId);
        order.verify(chapterRepository).delete(f.chapter);
        order.verify(chapterRepository).flush();
    }

    @Test
    void deleteChapterEvictsChapterAndComicCaches() {
        DeleteFixture f = deleteFixture();

        service.deleteChapter(f.comicId, f.chapterId, f.authorId);

        verify(chapterCrudPlugin).evictChaptersCache(f.comicId);
        verify(chapterCrudPlugin).evictChapterDetailCache(f.chapterId);
        verify(comicCrudPlugin).evictComicCache(f.comicId);
    }

    @Test
    void deleteChapterRefreshesComicMetadataAfterDeletion() {
        DeleteFixture f = deleteFixture();

        service.deleteChapter(f.comicId, f.chapterId, f.authorId);

        verify(chapterRepository)
                .findAllByComic_IdAndDeletedFalseAndModerationStatus(
                        f.comicId, ChapterStatus.PUBLISHED);
        verify(comicRepository).save(f.comic);
    }

    @Test
    void deleteChapterRevokesComicProfileSubmissionWhenComicBecomesEmpty() {
        DeleteFixture f = deleteFixture();

        service.deleteChapter(f.comicId, f.chapterId, f.authorId);

        verify(authorComicService)
                .revokeComicProfileSubmissionIfEmpty(f.comicId, f.authorId);
    }

    @Test
    void deleteChapterStillCompletesWhenRedisCleanupFails() {
        DeleteFixture f = deleteFixture();
        when(redisTemplate.opsForSet()).thenThrow(new RuntimeException("redis unavailable"));

        assertDoesNotThrow(() ->
                service.deleteChapter(f.comicId, f.chapterId, f.authorId)
        );

        verify(chapterRepository).delete(f.chapter);
        verify(chapterRepository).flush();
    }

    // ===== helpers =====

    private void assertFolderValidationError(
            List<MultipartFile> files,
            List<String> paths,
            int expectedCode
    ) {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, files, paths)
        );

        assertEquals(expectedCode, error.getCode());
        verifyNoInteractions(cloudinaryStorageService);
    }

    private void assertUploadAcceptedAtReportedImageSize(long reportedSize) throws Exception {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");
        stubCloudinaryUpload();

        MultipartFile file = mockValidPng("01.png", reportedSize);

        assertDoesNotThrow(() ->
                service.uploadChapterFolder(
                        comicId,
                        request,
                        List.of(file),
                        List.of("Chapter 1/01.png")
                )
        );
    }

    private void assertUploadAcceptedAtTotalLimit(long reportedSize, long configuredLimit)
            throws Exception {
        ReflectionTestUtils.setField(service, "maxTotalUploadSizeBytes", configuredLimit);

        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");

        stubUploadOwnership(comicId, authorId, "1");
        stubCloudinaryUpload();

        MultipartFile file = mockValidPng("01.png", reportedSize);

        assertDoesNotThrow(() ->
                service.uploadChapterFolder(
                        comicId,
                        request,
                        List.of(file),
                        List.of("Chapter 1/01.png")
                )
        );
    }

    private MultipartFile mockValidPng(String name, long reportedSize) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getOriginalFilename()).thenReturn(name);
        lenient().when(file.getSize()).thenReturn(reportedSize);
        lenient().when(file.getBytes()).thenReturn(pngBytes());
        return file;
    }

    private void stubUploadOwnership(UUID comicId, UUID authorId, String chapterNumber) {
        when(authorComicService.getOwnedComic(comicId, authorId))
                .thenReturn(comic(comicId, authorId));
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(
                comicId, chapterNumber))
                .thenReturn(false);
    }

    private void stubCloudinaryUpload() {
        when(cloudinaryStorageService.uploadImage(any(), anyString(), anyString()))
                .thenReturn(CloudinaryUploadResult.builder()
                        .secureUrl("https://cdn.test/page.png")
                        .build());
    }

    private void stubOwnedChapter(
            UUID comicId,
            UUID chapterId,
            UUID authorId,
            ChapterEntity chapter
    ) {
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
                chapterId, comicId, authorId))
                .thenReturn(Optional.of(chapter));
    }

    private void stubSubmitOwnership(
            UUID comicId,
            UUID chapterId,
            UUID authorId,
            ComicEntity comic,
            ChapterEntity chapter
    ) {
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        stubOwnedChapter(comicId, chapterId, authorId, chapter);
    }

    private UpdateFixture updateFixture(List<String> images, ChapterStatus status) {
        UpdateFixture f = new UpdateFixture();
        f.comicId = UUID.randomUUID();
        f.chapterId = UUID.randomUUID();
        f.authorId = UUID.randomUUID();
        f.comic = comic(f.comicId, f.authorId);
        f.chapter = chapter(f.comic, f.chapterId, "1", status, images);
        stubOwnedChapter(f.comicId, f.chapterId, f.authorId, f.chapter);
        return f;
    }

    private DeleteFixture deleteFixture() {
        DeleteFixture f = new DeleteFixture();
        f.comicId = UUID.randomUUID();
        f.chapterId = UUID.randomUUID();
        f.authorId = UUID.randomUUID();
        f.comic = comic(f.comicId, f.authorId);
        f.chapter = chapter(
                f.comic,
                f.chapterId,
                "1",
                ChapterStatus.PREVIEW_READY,
                List.of("a")
        );

        when(authorComicService.getOwnedComic(f.comicId, f.authorId))
                .thenReturn(f.comic);
        stubOwnedChapter(f.comicId, f.chapterId, f.authorId, f.chapter);
        return f;
    }

    private ComicEntity comic(UUID comicId, UUID authorId) {
        ComicEntity comic = ComicEntity.builder()
                .title("Comic")
                .authorId(authorId)
                .language("en")
                .build();
        comic.setId(comicId);
        return comic;
    }

    private ChapterEntity chapter(
            ComicEntity comic,
            UUID chapterId,
            String chapterNumber,
            ChapterStatus status,
            List<String> images
    ) {
        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber(chapterNumber)
                .moderationStatus(status)
                .images(images)
                .build();
        chapter.setId(chapterId);
        return chapter;
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", pngBytes());
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
    }

    private static class UpdateFixture {
        UUID comicId;
        UUID chapterId;
        UUID authorId;
        ComicEntity comic;
        ChapterEntity chapter;
    }

    private static class DeleteFixture {
        UUID comicId;
        UUID chapterId;
        UUID authorId;
        ComicEntity comic;
        ChapterEntity chapter;
    }
}
