package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorChapterServiceTest {

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
        lenient().when(chapterRepository.findAllByComic_IdAndDeletedFalseAndModerationStatus(any(), eq(ChapterStatus.PUBLISHED)))
                .thenReturn(List.of());
    }

    @Test
    void uploadChapterFolder_requiresRequestAuthorAndValidChapterNumber() {
        UUID comicId = UUID.randomUUID();
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadChapterFolder(comicId, null, List.of(), List.of())).getCode());

        ChapterUploadRequest noAuthor = new ChapterUploadRequest(null, "1", "Title");
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadChapterFolder(comicId, noAuthor, List.of(), List.of())).getCode());

        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest invalidNumber = new ChapterUploadRequest(authorId, "0", "Title");
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.uploadChapterFolder(comicId, invalidNumber, List.of(), List.of())).getCode());
    }

    @Test
    void uploadChapterFolder_enforcesAuthorLicenseBeforeOwnershipOrStorage() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");
        doThrow(new CustomException(403, "license inactive", org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authorLicenseService).assertPublishingAllowed(authorId);

        assertEquals(403, assertThrows(CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of())).getCode());
        verifyNoInteractions(authorComicService, cloudinaryStorageService);
    }

    @Test
    void uploadChapterFolder_rejectsDuplicateChapterNumber() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1,5", "Title");
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic(comicId, authorId));
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "1.5"))
                .thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> service.uploadChapterFolder(comicId, request, List.of(), List.of()));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("already exists"));
    }

    @Test
    void uploadChapterFolder_validatesFolderStructureAndFileTypeBoundaries() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1", "Title");
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic(comicId, authorId));
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "1"))
                .thenReturn(false);

        assertEquals(400, assertThrows(CustomException.class, () ->
                service.uploadChapterFolder(comicId, request, List.of(), List.of())).getCode());

        MockMultipartFile png = png("01.png");
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.uploadChapterFolder(comicId, request, List.of(png), List.of())).getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.uploadChapterFolder(comicId, request, List.of(png), List.of("../01.png"))).getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.uploadChapterFolder(comicId, request, List.of(png), List.of("ch1/sub/01.png"))).getCode());

        MockMultipartFile txt = new MockMultipartFile("file", "01.txt", "text/plain", "x".getBytes());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.uploadChapterFolder(comicId, request, List.of(txt), List.of("ch1/01.txt"))).getCode());
    }

    @Test
    void uploadChapterFolder_happyPathUploadsImageAndCreatesPreviewReadyChapter() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterUploadRequest request = new ChapterUploadRequest(authorId, "1,5", "  Chapter title  ");
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "1.5"))
                .thenReturn(false);
        when(chapterRepository.existsByContentHashAndModerationStatus(anyString(), eq(ChapterStatus.REJECTED)))
                .thenReturn(false);
        when(chapterPremiumPolicyService.isPremiumChapter("1.5")).thenReturn(true);
        when(cloudinaryStorageService.uploadImage(any(), startsWith("001-"), contains("chapter-1.5")))
                .thenReturn(CloudinaryUploadResult.builder().secureUrl("https://cdn.test/001.png").build());

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

    @Test
    void previewChapter_rejectsInvalidIdsAndMissingOwnership() {
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.previewChapter(null, UUID.randomUUID(), UUID.randomUUID())).getCode());

        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(chapterId, comicId, authorId))
                .thenReturn(Optional.empty());
        assertEquals(404, assertThrows(CustomException.class,
                () -> service.previewChapter(comicId, chapterId, authorId)).getCode());
    }

    @Test
    void submitForReview_requiresImagesAndBlocksDuplicateSubmission() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic).chapterNumber("1").moderationStatus(ChapterStatus.PREVIEW_READY).images(List.of()).build();
        chapter.setId(chapterId);
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(chapterId, comicId, authorId))
                .thenReturn(Optional.of(chapter));

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)).getCode());

        chapter.setImages(List.of("https://cdn/1.png"));
        chapter.setModerationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW);
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.submitForReview(comicId, chapterId, authorId)).getCode());
    }

    @Test
    void submitForReview_happyPathCreatesSubmissionAndChangesStatus() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber("2")
                .moderationStatus(ChapterStatus.PREVIEW_READY)
                .images(List.of("a", "b"))
                .build();
        chapter.setId(chapterId);
        when(authorComicService.getOwnedComic(comicId, authorId)).thenReturn(comic);
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(chapterId, comicId, authorId))
                .thenReturn(Optional.of(chapter));

        var response = service.submitForReview(comicId, chapterId, authorId);

        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, chapter.getModerationStatus());
        assertEquals(ChapterStatus.SUBMITTED_FOR_REVIEW, response.getStatus());
        verify(submissionRepository).save(argThat(s -> "pending".equalsIgnoreCase(s.getStatus())));
        verify(notificationService).notifyModeratorsWithLanguage(
                eq(comic.getLanguage()), contains("review"), contains("Chapter 2"), eq("UPDATE"), any());
    }

    @Test
    void updateChapter_rejectsNullRequestAndDuplicateNumber() {
        UUID comicId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.updateChapter(comicId, chapterId, authorId, null)).getCode());

        ComicEntity comic = comic(comicId, authorId);
        ChapterEntity chapter = ChapterEntity.builder().comic(comic).chapterNumber("1").images(List.of("a")).build();
        chapter.setId(chapterId);
        when(chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(chapterId, comicId, authorId))
                .thenReturn(Optional.of(chapter));
        when(chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, "2"))
                .thenReturn(true);

        assertEquals(409, assertThrows(CustomException.class,
                () -> service.updateChapter(comicId, chapterId, authorId,
                        new ChapterUploadRequest(authorId, "2", null))).getCode());
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

    private MockMultipartFile png(String name) {
        byte[] bytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        return new MockMultipartFile("file", name, "image/png", bytes);
    }
}
