package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicAppealRequest;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ComicMetricsResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.repository.projection.ComicChapterCountProjection;
import com.sep.comiverse.service.AuditLogService;
import com.sep.comiverse.service.AuthorComicService;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorComicServiceTest {

    @Mock private IComicRepository comicRepository;
    @Mock private IGenreRepository genreRepository;
    @Mock private IChapterRepository chapterRepository;
    @Mock private ISubmissionRepository submissionRepository;
    @Mock private IComicMetricSnapshotRepository metricSnapshotRepository;
    @Mock private NotificationService notificationService;
    @Mock private IUserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ComicCrudPlugin comicCrudPlugin;
    @Mock private AuthorLicenseService authorLicenseService;
    @Mock private CreatorPayoutSettingsService payoutSettingsService;
    @Mock private CreatorPayoutSettingsService payoutSettingsService;

    private AuthorComicService service;
    private GenreEntity defaultGenre;

    @BeforeEach
    void setUp() {
        service = new AuthorComicService(
                comicRepository,
                genreRepository,
                chapterRepository,
                submissionRepository,
                metricSnapshotRepository,
                notificationService,
                userRepository,
                auditLogService,
                comicCrudPlugin,
                authorLicenseService,
                payoutSettingsService
                authorLicenseService,
                payoutSettingsService
        );

        defaultGenre = genre("Action", "action");
        lenient().when(genreRepository.findAll()).thenReturn(List.of(defaultGenre));

        CreatorPayoutSettingEntity payoutSettings = CreatorPayoutSettingEntity.builder()
                .authorViewsPerUnit(1_000L)
                .authorViewUnitRateUsd(new BigDecimal("40.00"))
                .build();
        lenient().when(payoutSettingsService.currentSettings()).thenReturn(payoutSettings);
    }

    @Test
    void publishedComicQuotaRejectsTheOneThousandAndFirstPublicComic() {
        UUID authorId = UUID.randomUUID();
        ComicEntity comic = ComicEntity.builder()
                .authorId(authorId)
                .title("Quota Comic")
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .build();

        when(comicRepository.countByAuthorIdAndModerationStatusAndDeletedFalse(
                authorId,
                ComicModerationStatus.PUBLISHED
        )).thenReturn(1000L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.assertPublishedComicQuotaAvailable(comic)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("Published comic limit reached"));
    }

    // ===== createComic =====

    @Test
    void createComicNormalizesInputAndStartsAsDraft() {
        UUID authorId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(authorId);

        when(comicRepository.save(any(ComicEntity.class))).thenAnswer(invocation -> {
            ComicEntity saved = invocation.getArgument(0);
            saved.setId(comicId);
            return saved;
        });
        when(chapterRepository.countByComic_IdAndDeletedFalse(comicId)).thenReturn(0L);

        AuthorComicResponse response = service.createComic(request);

        ArgumentCaptor<ComicEntity> captor = ArgumentCaptor.forClass(ComicEntity.class);
        verify(comicRepository).save(captor.capture());
        verify(authorLicenseService).assertPublishingAllowed(authorId);

        ComicEntity saved = captor.getValue();
        assertEquals(authorId, saved.getAuthorId());
        assertEquals("My Comic", saved.getTitle());
        assertEquals("A story summary", saved.getSummary());
        assertEquals("English", saved.getLanguage());
        assertEquals(13, saved.getMinimumAge());
        assertEquals("https://cdn.example/cover.jpg", saved.getCover());
        assertEquals(ComicPublicationStatus.ONGOING, saved.getPublicationStatus());
        assertEquals(ComicModerationStatus.DRAFT, saved.getModerationStatus());
        assertEquals(0L, saved.getViewCount());
        assertEquals(0, saved.getSaveCount());
        assertEquals(0, saved.getLikeCount());
        assertEquals(0, saved.getRatingCount());
        assertEquals(0, saved.getChapterCount());
        assertEquals(comicId, response.getId());
        assertEquals(0, response.getChapterCount());
    }

    @Test
    void createComicRejectsWhenActiveDraftAndReworkQuotaIsFull() {
        UUID authorId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(authorId);

        when(comicRepository.countByAuthorIdAndModerationStatusInAndDeletedFalse(
                eq(authorId),
                anyCollection()
        )).thenReturn(30L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("Active comic draft/rework limit reached"));
        verify(comicRepository, never()).save(any(ComicEntity.class));
        verify(authorLicenseService).assertPublishingAllowed(authorId);
    }

    @Test
    void createComicRejectsMissingRequest() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(null)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(
                authorLicenseService,
                comicRepository,
                genreRepository,
                chapterRepository,
                submissionRepository
        );
    }

    @Test
    void createComicRejectsMissingAuthorId() {
        AuthorComicCreateRequest request = createRequest(null);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertEquals("Author id is required", error.getMessage());
        verifyNoInteractions(authorLicenseService, comicRepository, genreRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void createComicRejectsBlankTitle(String title) {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setTitle(title);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertEquals("Title is required", error.getMessage());
        verifyNoInteractions(authorLicenseService, comicRepository, genreRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void createComicRejectsBlankLanguage(String language) {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setLanguage(language);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertEquals("Comic language is required", error.getMessage());
        verifyNoInteractions(authorLicenseService, comicRepository, genreRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void createComicRejectsBlankCoverBeforeRepositoryCalls(String cover) {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setCover(cover);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertEquals("Cover image is required", error.getMessage());
        verifyNoInteractions(authorLicenseService, comicRepository, genreRepository);
    }

    @Test
    void createComicStopsBeforePersistenceWhenAuthorLicenseDoesNotAllowPublishing() {
        UUID authorId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(authorId);
        CustomException licenseError = new CustomException(
                403,
                "Author license must be verified before publishing",
                HttpStatus.FORBIDDEN
        );
        doThrow(licenseError)
                .when(authorLicenseService)
                .assertPublishingAllowed(authorId);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(403, error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getHttpStatus());
        verify(comicRepository, never()).save(any());
        verifyNoInteractions(genreRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 22})
    void createComicRejectsMinimumAgeOutsideAllowedRange(int minimumAge) {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setMinimumAge(minimumAge);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("between 0 and 21"));
        verify(comicRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 20, 21})
    void createComicAcceptsMinimumAgeBoundaryValues(int minimumAge) {
        UUID comicId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setMinimumAge(minimumAge);

        stubCreateSave(comicId);

        AuthorComicResponse response = service.createComic(request);

        ArgumentCaptor<ComicEntity> captor = ArgumentCaptor.forClass(ComicEntity.class);
        verify(comicRepository).save(captor.capture());
        assertEquals(minimumAge, captor.getValue().getMinimumAge());
        assertEquals(minimumAge, response.getMinimumAge());
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 100})
    void createComicAcceptsLanguageAtAndBelowMaximumLength(int length) {
        UUID comicId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setLanguage("a".repeat(length));
        stubCreateSave(comicId);

        AuthorComicResponse response = service.createComic(request);

        assertEquals(length, response.getLanguage().length());
    }

    @Test
    void createComicRejectsLanguageAboveMaximumLength() {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setLanguage("a".repeat(101));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("100 characters"));
        verify(comicRepository, never()).save(any());
    }

    @Test
    void createComicReusesExistingGenreIgnoringCase() {
        UUID comicId = UUID.randomUUID();
        GenreEntity action = genre("Action", "action");
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setGenres(List.of(" action "));

        when(genreRepository.findAll()).thenReturn(List.of(action));
        stubCreateSave(comicId);

        service.createComic(request);

        ArgumentCaptor<ComicEntity> captor = ArgumentCaptor.forClass(ComicEntity.class);
        verify(comicRepository).save(captor.capture());
        assertEquals(Set.of(action), captor.getValue().getGenres());
        verify(genreRepository, never()).save(any());
    }

    @Test
    void createComicCreatesMissingGenreWithNormalizedSlug() {
        UUID comicId = UUID.randomUUID();
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setGenres(List.of(" Truyện Hành Trình "));

        when(genreRepository.findAll()).thenReturn(List.of());
        when(genreRepository.save(any(GenreEntity.class))).thenAnswer(invocation -> {
            GenreEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        stubCreateSave(comicId);

        service.createComic(request);

        ArgumentCaptor<GenreEntity> genreCaptor = ArgumentCaptor.forClass(GenreEntity.class);
        verify(genreRepository).save(genreCaptor.capture());
        assertEquals("Truyện Hành Trình", genreCaptor.getValue().getName());
        assertEquals("truyen-hanh-trinh", genreCaptor.getValue().getSlug());
    }

    @Test
    void createComicIgnoresBlankAndDuplicateGenreNames() {
        UUID comicId = UUID.randomUUID();
        GenreEntity action = genre("Action", "action");
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setGenres(List.of("Action", "Action", " ", ""));

        when(genreRepository.findAll()).thenReturn(List.of(action));
        stubCreateSave(comicId);

        service.createComic(request);

        ArgumentCaptor<ComicEntity> captor = ArgumentCaptor.forClass(ComicEntity.class);
        verify(comicRepository).save(captor.capture());
        assertEquals(Set.of(action), captor.getValue().getGenres());
    }

    @Test
    void createComicCollapsesExistingGenreDuplicatesIgnoringCaseAndWhitespace() {
        UUID comicId = UUID.randomUUID();
        GenreEntity action = genre("Action", "action");
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setGenres(List.of("Action", " action ", "ACTION"));

        when(genreRepository.findAll()).thenReturn(List.of(action));
        stubCreateSave(comicId);

        service.createComic(request);

        ArgumentCaptor<ComicEntity> captor =
                ArgumentCaptor.forClass(ComicEntity.class);
        verify(comicRepository).save(captor.capture());

        assertEquals(Set.of(action), captor.getValue().getGenres());
        verify(genreRepository, never()).save(any());
    }

    @Test
    void createComicRejectsGenreWhoseNormalizedSlugIsEmpty() {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setGenres(List.of("!!!"));
        when(genreRepository.findAll()).thenReturn(List.of());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createComic(request)
        );

        assertEquals(400, error.getCode());
        assertEquals("Genre name is invalid", error.getMessage());
        verify(comicRepository, never()).save(any());
    }

    // ===== getOwnedComic / getComic =====

    @Test
    void getOwnedComicRejectsMissingComicId() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.getOwnedComic(null, UUID.randomUUID())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(comicRepository);
    }

    @Test
    void getOwnedComicRejectsMissingAuthorId() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.getOwnedComic(UUID.randomUUID(), null)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(comicRepository);
    }

    @Test
    void getOwnedComicDoesNotExposeAnotherAuthorsComic() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(comicId, authorId))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.getOwnedComic(comicId, authorId)
        );

        assertEquals(404, error.getCode());
        assertEquals(
                "Comic not found or does not belong to this author",
                error.getMessage()
        );
    }

    @ParameterizedTest
    @CsvSource({
            "pending,SUBMITTED_FOR_REVIEW",
            "approved,PUBLISHED",
            "rejected,REJECTED",
            "unknown,DRAFT"
    })
    void getComicMapsLatestProfileSubmissionStatusWhenComicStatusMissing(
            String submissionStatus,
            ComicModerationStatus expected
    ) {
        ComicEntity comic = ownedComic(null);
        SubmissionEntity latest = SubmissionEntity.builder()
                .status(submissionStatus)
                .build();

        stubOwnedComic(comic);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(),
                        comic.getAuthorId(),
                        "author"
                ))
                .thenReturn(Optional.of(latest));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(2L);

        AuthorComicResponse response =
                service.getComic(comic.getId(), comic.getAuthorId());

        assertEquals(expected, response.getModerationStatus());
        assertEquals(2, response.getChapterCount());
    }

    @Test
    void getComicDefaultsMissingModerationAndSubmissionToDraft() {
        ComicEntity comic = ownedComic(null);
        stubOwnedComic(comic);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(),
                        comic.getAuthorId(),
                        "author"
                ))
                .thenReturn(Optional.empty());
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(0L);

        AuthorComicResponse response =
                service.getComic(comic.getId(), comic.getAuthorId());

        assertEquals(ComicModerationStatus.DRAFT, response.getModerationStatus());
    }

    // ===== listOwnComics =====

    @Test
    void listOwnComicsRejectsMissingAuthorId() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.listOwnComics(null, new PaginationSearchDTO())
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(comicRepository, chapterRepository);
    }

    @Test
    void listOwnComicsUsesDefaultPaginationAndFastPathWhenPaginationMissing() {
        UUID authorId = UUID.randomUUID();
        when(comicRepository.findByAuthorIdAndDeletedFalse(
                eq(authorId),
                any(PageRequest.class)
        )).thenReturn(Page.empty());

        Page<AuthorComicResponse> result =
                service.listOwnComics(authorId, null);

        assertTrue(result.isEmpty());
        verify(comicRepository).findByAuthorIdAndDeletedFalse(
                eq(authorId),
                argThat(pageable ->
                        pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 10
                )
        );
        verify(comicRepository, never()).searchAuthorComics(any(), any(), any());
    }

    @Test
    void listOwnComicsUsesProvidedPagination() {
        UUID authorId = UUID.randomUUID();
        PaginationSearchDTO pagination = new PaginationSearchDTO();
        pagination.setPage(3);
        pagination.setSize(25);

        when(comicRepository.findByAuthorIdAndDeletedFalse(
                eq(authorId),
                any(PageRequest.class)
        )).thenReturn(Page.empty());

        service.listOwnComics(authorId, pagination);

        ArgumentCaptor<PageRequest> pageableCaptor =
                ArgumentCaptor.forClass(PageRequest.class);
        verify(comicRepository).findByAuthorIdAndDeletedFalse(
                eq(authorId),
                pageableCaptor.capture()
        );

        PageRequest pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
        verify(comicRepository, never()).searchAuthorComics(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void listOwnComicsUsesFastPathWhenSearchMissingOrBlank(String search) {
        UUID authorId = UUID.randomUUID();
        PaginationSearchDTO pagination = new PaginationSearchDTO();
        pagination.setSearch(search);

        when(comicRepository.findByAuthorIdAndDeletedFalse(
                eq(authorId),
                any(PageRequest.class)
        )).thenReturn(Page.empty());

        service.listOwnComics(authorId, pagination);

        verify(comicRepository).findByAuthorIdAndDeletedFalse(
                eq(authorId),
                any(PageRequest.class)
        );
        verify(comicRepository, never()).searchAuthorComics(any(), any(), any());
    }

    @Test
    void listOwnComicsUsesSearchRepositoryWhenKeywordProvided() {
        UUID authorId = UUID.randomUUID();
        PaginationSearchDTO pagination = new PaginationSearchDTO();
        pagination.setSearch("dragon");

        when(comicRepository.searchAuthorComics(
                eq(authorId),
                eq("dragon"),
                any(PageRequest.class)
        )).thenReturn(Page.empty());

        service.listOwnComics(authorId, pagination);

        verify(comicRepository).searchAuthorComics(
                eq(authorId),
                eq("dragon"),
                any(PageRequest.class)
        );
        verify(comicRepository, never())
                .findByAuthorIdAndDeletedFalse(any(), any());
    }

    @Test
    void listOwnComicsMapsBatchChapterCountsWithoutPerComicCountQueries() {
        UUID authorId = UUID.randomUUID();
        ComicEntity first = ownedComic(ComicModerationStatus.DRAFT);
        ComicEntity second = ownedComic(ComicModerationStatus.PUBLISHED);

        Page<ComicEntity> comicPage = new PageImpl<>(List.of(first, second));
        when(comicRepository.findByAuthorIdAndDeletedFalse(
                eq(authorId),
                any(PageRequest.class)
        )).thenReturn(comicPage);

        ComicChapterCountProjection firstCount =
                chapterCountProjection(first.getId(), 3L);
        ComicChapterCountProjection secondCount =
                chapterCountProjection(second.getId(), 5L);

        when(chapterRepository.countActiveChaptersByComicIds(
                argThat(ids ->
                        ids.size() == 2
                                && ids.contains(first.getId())
                                && ids.contains(second.getId())
                )
        )).thenReturn(List.of(firstCount, secondCount));

        Page<AuthorComicResponse> result =
                service.listOwnComics(authorId, new PaginationSearchDTO());

        assertEquals(2, result.getTotalElements());
        assertEquals(3, result.getContent().get(0).getChapterCount());
        assertEquals(5, result.getContent().get(1).getChapterCount());
        verify(chapterRepository, never())
                .countByComic_IdAndDeletedFalse(any());
    }

    // ===== updateComic =====

    @Test
    void updateComicRejectsMissingRequest() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateComic(UUID.randomUUID(), null)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(comicRepository);
    }

    @Test
    void updateComicRejectsMissingAuthorId() {
        AuthorComicUpdateRequest request = new AuthorComicUpdateRequest();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateComic(UUID.randomUUID(), request)
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(comicRepository);
    }

    @Test
    void updateComicUnchangedTrimmedTitleDoesNotCancelModerationSubmission() {
        ComicEntity comic = ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        stubOwnedComic(comic);
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setTitle("  My Comic  ");

        AuthorComicResponse response =
                service.updateComic(comic.getId(), request);

        assertEquals(ComicModerationStatus.SUBMITTED_FOR_REVIEW, comic.getModerationStatus());
        assertEquals(ComicModerationStatus.SUBMITTED_FOR_REVIEW, response.getModerationStatus());
        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
    }

    @Test
    void editingTitleCancelsPendingProfileSubmissionAndReturnsToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        SubmissionEntity pending = pendingComicSubmission(comic);
        stubPendingComicEdit(comic, pending);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setTitle("  Updated Comic Title  ");

        AuthorComicResponse response =
                service.updateComic(comic.getId(), request);

        assertEquals("Updated Comic Title", comic.getTitle());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
        assertEquals("cancelled", pending.getStatus());
        assertEquals(Boolean.TRUE, pending.getDeleted());
        assertEquals(ComicModerationStatus.DRAFT, response.getModerationStatus());
        verify(submissionRepository).save(pending);
    }

    @Test
    void editingSummaryReturnsModeratedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubOwnedComic(comic);
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of());
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setSummary(" New summary ");

        service.updateComic(comic.getId(), request);

        assertEquals("New summary", comic.getSummary());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
    }

    @Test
    void editingLanguageReturnsModeratedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubUpdateNoPending(comic);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setLanguage(" Vietnamese ");

        service.updateComic(comic.getId(), request);

        assertEquals("Vietnamese", comic.getLanguage());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
    }

    @Test
    void updateComicRejectsLanguageAboveMaximumLength() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubOwnedComic(comic);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setLanguage("a".repeat(101));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateComic(comic.getId(), request)
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("100 characters"));
        assertEquals("English", comic.getLanguage());
        assertEquals(ComicModerationStatus.PUBLISHED, comic.getModerationStatus());
        verify(comicRepository, never()).save(any());
        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
    }

    @Test
    void editingMinimumAgeReturnsModeratedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubUpdateNoPending(comic);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setMinimumAge(18);

        service.updateComic(comic.getId(), request);

        assertEquals(18, comic.getMinimumAge());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 22})
    void updateComicRejectsMinimumAgeOutsideAllowedRange(int minimumAge) {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubOwnedComic(comic);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setMinimumAge(minimumAge);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateComic(comic.getId(), request)
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("between 0 and 21"));
        assertEquals(13, comic.getMinimumAge());
        assertEquals(ComicModerationStatus.PUBLISHED, comic.getModerationStatus());
        verify(comicRepository, never()).save(any());
        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
    }

    @Test
    void editingCoverReturnsModeratedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubUpdateNoPending(comic);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setCover(" https://cdn.example/new-cover.jpg ");

        service.updateComic(comic.getId(), request);

        assertEquals("https://cdn.example/new-cover.jpg", comic.getCover());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
    }

    @Test
    void equivalentGenresDoNotResetModerationStatusOrCancelSubmission() {
        ComicEntity comic =
                ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        GenreEntity action = genre("Action", "action");
        GenreEntity drama = genre("Drama", "drama");
        comic.setGenres(Set.of(action, drama));

        stubOwnedComic(comic);
        when(genreRepository.findAll()).thenReturn(List.of(action, drama));
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);

        AuthorComicUpdateRequest request =
                updateRequest(comic.getAuthorId());
        request.setGenres(List.of(" drama ", "ACTION"));

        AuthorComicResponse response =
                service.updateComic(comic.getId(), request);

        assertEquals(Set.of(action, drama), comic.getGenres());
        assertEquals(
                ComicModerationStatus.SUBMITTED_FOR_REVIEW,
                comic.getModerationStatus()
        );
        assertEquals(
                ComicModerationStatus.SUBMITTED_FOR_REVIEW,
                response.getModerationStatus()
        );

        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
    }

    @Test
    void editingGenresReturnsModeratedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        GenreEntity oldGenre = genre("Action", "action");
        GenreEntity newGenre = genre("Drama", "drama");
        comic.setGenres(Set.of(oldGenre));

        stubOwnedComic(comic);
        when(genreRepository.findAll()).thenReturn(List.of(oldGenre, newGenre));
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of());
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setGenres(List.of("Drama"));

        service.updateComic(comic.getId(), request);

        assertEquals(Set.of(newGenre), comic.getGenres());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
    }

    @Test
    void updateComicChangingPublicationStatusDoesNotResetModerationStatus() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubOwnedComic(comic);
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);

        AuthorComicUpdateRequest request = updateRequest(comic.getAuthorId());
        request.setPublicationStatus(ComicPublicationStatus.COMPLETED);

        AuthorComicResponse response =
                service.updateComic(comic.getId(), request);

        assertEquals(ComicPublicationStatus.COMPLETED, comic.getPublicationStatus());
        assertEquals(ComicModerationStatus.PUBLISHED, comic.getModerationStatus());
        assertEquals(ComicModerationStatus.PUBLISHED, response.getModerationStatus());
        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
    }

    // ===== submitForReview =====

    @Test
    void submitForReviewStopsWhenLicenseDoesNotAllowPublishing() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        CustomException licenseError = new CustomException(
                403,
                "Author license must be verified before publishing",
                HttpStatus.FORBIDDEN
        );
        doThrow(licenseError)
                .when(authorLicenseService)
                .assertPublishingAllowed(authorId);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comicId, authorId)
        );

        assertEquals(403, error.getCode());
        verifyNoInteractions(comicRepository, chapterRepository, submissionRepository);
    }

    @Test
    void submitForReviewRequiresAtLeastOneChapter() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(0L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(400, error.getCode());
        assertEquals(
                "Comic must have at least one chapter before it can be submitted for review",
                error.getMessage()
        );
        verify(submissionRepository, never()).save(any());
        verify(notificationService, never())
                .notifyModeratorsWithLanguage(any(), any(), any(), any(), any());
    }

    @Test
    void submitForReviewRejectsPublishedComic() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsAlreadySubmittedComicStatus() {
        ComicEntity comic = ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId(), "author"
                ))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsDuplicatePendingSubmission() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        SubmissionEntity pending = SubmissionEntity.builder().status("pending").build();

        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId(), "author"
                ))
                .thenReturn(Optional.of(pending));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(409, error.getCode());
        verify(submissionRepository, never()).save(any());
        verify(comicRepository, never()).save(any());
    }

    @Test
    void submitForReviewRejectsWhenAuthorAlreadyHasFivePendingComicReviews() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId(), "author"
                ))
                .thenReturn(Optional.empty());
        when(submissionRepository
                .countByAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getAuthorId(), "author", "pending"
                ))
                .thenReturn(5L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("Comic review queue limit reached"));
        verify(submissionRepository, never()).save(any(SubmissionEntity.class));
        verify(comicRepository, never()).save(any(ComicEntity.class));
    }

    @Test
    void submitForReviewCreatesModeratorQueueItemAndNotification() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId(), "author"
                ))
                .thenReturn(Optional.empty());
        when(comicRepository.save(comic)).thenReturn(comic);

        AuthorComicResponse response =
                service.submitForReview(comic.getId(), comic.getAuthorId());

        ArgumentCaptor<SubmissionEntity> submissionCaptor =
                ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionRepository).save(submissionCaptor.capture());

        SubmissionEntity submission = submissionCaptor.getValue();
        assertEquals(comic.getId(), submission.getComicId());
        assertEquals(comic.getAuthorId(), submission.getAuthorId());
        assertEquals("author", submission.getQueueType());
        assertEquals("pending", submission.getStatus());
        assertEquals("Comic profile", submission.getChapter());
        assertEquals(ComicModerationStatus.SUBMITTED_FOR_REVIEW, comic.getModerationStatus());
        assertEquals(ComicModerationStatus.SUBMITTED_FOR_REVIEW, response.getModerationStatus());

        verify(notificationService).notifyModeratorsWithLanguage(
                "English",
                "New comic review",
                "My Comic was submitted by an author for moderation.",
                "UPDATE",
                NotificationPreferenceKey.REVIEW_QUEUE
        );
    }

    @Test
    void submitForReviewAllowsRejectedComicToBeResubmitted() {
        ComicEntity comic = ownedComic(ComicModerationStatus.REJECTED);
        stubOwnedComic(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
        when(submissionRepository
                .findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId(), "author"
                ))
                .thenReturn(Optional.of(SubmissionEntity.builder().status("rejected").build()));
        when(comicRepository.save(comic)).thenReturn(comic);

        AuthorComicResponse response =
                service.submitForReview(comic.getId(), comic.getAuthorId());

        assertEquals(ComicModerationStatus.SUBMITTED_FOR_REVIEW, response.getModerationStatus());
        verify(submissionRepository).save(any(SubmissionEntity.class));
    }

    // ===== deleteComic =====

    @Test
    void deleteComicSoftDeletesComicAndChaptersAndCancelsPendingSubmissions() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        ChapterEntity first = new ChapterEntity();
        first.setId(UUID.randomUUID());
        first.setDeleted(false);
        ChapterEntity second = new ChapterEntity();
        second.setId(UUID.randomUUID());
        second.setDeleted(false);

        SubmissionEntity firstChapterPending = SubmissionEntity.builder()
                .status("pending").build();
        SubmissionEntity secondChapterPending = SubmissionEntity.builder()
                .status("pending").build();
        SubmissionEntity comicPending = pendingComicSubmission(comic);

        stubOwnedComic(comic);
        when(chapterRepository.findAllByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(List.of(first, second));
        when(submissionRepository
                .findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        first.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of(firstChapterPending));
        when(submissionRepository
                .findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        second.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of(secondChapterPending));
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of(comicPending));

        service.deleteComic(comic.getId(), comic.getAuthorId());

        assertEquals(Boolean.TRUE, first.getDeleted());
        assertEquals(Boolean.TRUE, second.getDeleted());
        assertEquals(Boolean.TRUE, comic.getDeleted());

        assertCancelled(firstChapterPending);
        assertCancelled(secondChapterPending);
        assertCancelled(comicPending);

        verify(chapterRepository).save(first);
        verify(chapterRepository).save(second);
        verify(comicRepository).save(comic);
        verify(submissionRepository, times(3)).save(any(SubmissionEntity.class));
    }

    // ===== revokeComicProfileSubmissionIfEmpty =====

    @Test
    void revokeComicProfileSubmissionIfEmptyReturnsSubmittedComicToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        SubmissionEntity pending = pendingComicSubmission(comic);

        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(0L);
        stubOwnedComic(comic);
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of(pending));

        service.revokeComicProfileSubmissionIfEmpty(
                comic.getId(),
                comic.getAuthorId()
        );

        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
        assertCancelled(pending);
        verify(comicRepository).save(comic);
    }

    @Test
    void revokeComicProfileSubmissionIfEmptyDoesNothingWhenChapterStillExists() {
        UUID comicId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(chapterRepository.countByComic_IdAndDeletedFalse(comicId))
                .thenReturn(1L);

        service.revokeComicProfileSubmissionIfEmpty(comicId, authorId);

        verifyNoInteractions(comicRepository, submissionRepository);
    }

    @Test
    void revokeComicProfileSubmissionIfEmptyDoesNothingWhenComicIsNotSubmitted() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(0L);
        stubOwnedComic(comic);

        service.revokeComicProfileSubmissionIfEmpty(
                comic.getId(),
                comic.getAuthorId()
        );

        verify(submissionRepository, never())
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        any(), any(), any(), any()
                );
        verify(comicRepository, never()).save(any());
    }

    // ===== getComicMetrics =====

    @Test
    void getComicMetricsUsesLatestSnapshotAndComicCounters() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        comic.setViewCount(1200L);
        comic.setSaveCount(30);
        comic.setLikeCount(45);
        comic.setRatingAverage(4.5);
        comic.setRatingCount(20);

        ComicMetricSnapshotEntity snapshot = ComicMetricSnapshotEntity.builder()
                .comicId(comic.getId())
                .authorId(comic.getAuthorId())
                .estimatedRevenue(new BigDecimal("123.45"))
                .build();
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        snapshot.setCreatedAt(createdAt);

        stubOwnedComic(comic);
        when(metricSnapshotRepository
                .findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId()
                )).thenReturn(Optional.of(snapshot));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(7L);

        ComicMetricsResponse response =
                service.getComicMetrics(comic.getId(), comic.getAuthorId());

        assertEquals(1200L, response.getViewCount());
        assertEquals(30L, response.getFollowCount());
        assertEquals(30L, response.getFavoriteCount());
        assertEquals(45L, response.getLikeCount());
        assertEquals(7, response.getChapterCount());
        assertEquals(4.5, response.getRatingAverage());
        assertEquals(20, response.getRatingCount());
        assertEquals(new BigDecimal("123.45"), response.getEstimatedRevenue());
        assertEquals(Date.from(createdAt), response.getSnapshotAt());
    }

    @Test
    void getComicMetricsFallsBackToConfiguredViewRevenueWhenSnapshotRevenueMissing() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        comic.setViewCount(2_500L);

        ComicMetricSnapshotEntity snapshot = ComicMetricSnapshotEntity.builder()
                .comicId(comic.getId())
                .authorId(comic.getAuthorId())
                .estimatedRevenue(null)
                .build();
        Instant createdAt = Instant.parse("2026-08-02T00:00:00Z");
        snapshot.setCreatedAt(createdAt);

        stubOwnedComic(comic);
        when(metricSnapshotRepository
                .findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId()
                )).thenReturn(Optional.of(snapshot));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);

        ComicMetricsResponse response =
                service.getComicMetrics(comic.getId(), comic.getAuthorId());

        // 2,500 views × (40 USD / 1,000 views) = 100.00 USD
        assertEquals(new BigDecimal("100.00"), response.getEstimatedRevenue());
        assertEquals(Date.from(createdAt), response.getSnapshotAt());
        assertEquals(1, response.getChapterCount());
    }

    @Test
    void getComicMetricsDefaultsMissingCountersAndSnapshotToZero() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        comic.setViewCount(null);
        comic.setSaveCount(null);
        comic.setLikeCount(null);
        comic.setRatingAverage(null);
        comic.setRatingCount(null);

        stubOwnedComic(comic);
        when(metricSnapshotRepository
                .findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
                        comic.getId(), comic.getAuthorId()
                )).thenReturn(Optional.empty());
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(0L);

        ComicMetricsResponse response =
                service.getComicMetrics(comic.getId(), comic.getAuthorId());

        assertEquals(0L, response.getViewCount());
        assertEquals(0L, response.getFollowCount());
        assertEquals(0L, response.getFavoriteCount());
        assertEquals(0L, response.getLikeCount());
        assertEquals(0, response.getChapterCount());
        assertEquals(0.0, response.getRatingAverage());
        assertEquals(0, response.getRatingCount());
        assertEquals(BigDecimal.ZERO, response.getEstimatedRevenue());
        assertNotNull(response.getSnapshotAt());
    }

    // ===== confirmModEdit =====

    @Test
    void confirmModEditClearsModeratorEditStateEvictsCacheAndAudits() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        comic.setIsModEdited(true);
        comic.setPreviousStateSnapshot("{\"title\":\"Old\"}");
        stubOwnedComic(comic);

        service.confirmModEdit(comic.getId(), comic.getAuthorId());

        assertFalse(Boolean.TRUE.equals(comic.getIsModEdited()));
        assertNull(comic.getPreviousStateSnapshot());
        verify(comicRepository).save(comic);
        verify(comicCrudPlugin).evictComicCache(comic.getId());
        verify(auditLogService).log(
                "COMIC_AUTHOR",
                "Author confirmed moderator edit for comic " + comic.getId()
        );
    }

    @Test
    void confirmModEditStillSucceedsWhenCacheEvictionFails() {
        ComicEntity comic = ownedComic(ComicModerationStatus.PUBLISHED);
        comic.setIsModEdited(true);
        stubOwnedComic(comic);
        doThrow(new RuntimeException("cache unavailable"))
                .when(comicCrudPlugin)
                .evictComicCache(comic.getId());

        assertDoesNotThrow(
                () -> service.confirmModEdit(comic.getId(), comic.getAuthorId())
        );

        verify(comicRepository).save(comic);
        verify(auditLogService).log(eq("COMIC_AUTHOR"), anyString());
    }

    // ===== submitAppeal =====

    @Test
    void submitAppealRejectsMissingRequest() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitAppeal(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null
                )
        );

        assertEquals(400, error.getCode());
        assertEquals("Appeal statement cannot be blank", error.getMessage());
        verifyNoInteractions(
                comicRepository,
                notificationService,
                auditLogService,
                userRepository
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void submitAppealRejectsBlankReason(String reason) {
        AuthorComicAppealRequest request = AuthorComicAppealRequest.builder()
                .category("CONTENT")
                .reason(reason)
                .build();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitAppeal(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        request
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(
                comicRepository,
                notificationService,
                auditLogService,
                userRepository
        );
    }

    @Test
    void submitAppealSetsAppealStateAndNotifiesReviewRoles() {
        ComicEntity comic = ownedComic(ComicModerationStatus.REJECTED);
        UserEntity author = UserEntity.builder()
                .fullName("Author Name")
                .username("author-user")
                .email("author@example.com")
                .build();

        stubOwnedComic(comic);
        when(userRepository.findById(comic.getAuthorId()))
                .thenReturn(Optional.of(author));

        AuthorComicAppealRequest request = AuthorComicAppealRequest.builder()
                .category("content_quality")
                .reason("  The moderation decision should be reviewed again.  ")
                .build();

        service.submitAppeal(comic.getId(), comic.getAuthorId(), request);

        assertEquals(ComicModerationStatus.UNPUBLISHED, comic.getModerationStatus());
        assertTrue(Boolean.TRUE.equals(comic.getIsAppealed()));
        assertEquals(
                "The moderation decision should be reviewed again.",
                comic.getAppealReason()
        );
        verify(comicRepository).save(comic);
        verify(comicCrudPlugin).evictComicCache(comic.getId());
        verify(auditLogService).log(
                eq("COMIC_APPEAL"),
                contains("Author Author Name submitted appeal")
        );
        verify(notificationService).notifyRoles(
                eq(List.of("MODERATOR", "ADMIN")),
                eq("Author Appeal: My Comic"),
                contains("[Content Quality]"),
                eq("APPEAL"),
                eq(NotificationPreferenceKey.REVIEW_QUEUE),
                eq("/moderator/comic/" + comic.getId())
        );
    }

    @Test
    void submitAppealDefaultsMissingCategoryAndUsesUsernameWhenFullNameMissing() {
        ComicEntity comic = ownedComic(ComicModerationStatus.REJECTED);
        UserEntity author = UserEntity.builder()
                .fullName(" ")
                .username("author-user")
                .email("author@example.com")
                .build();

        stubOwnedComic(comic);
        when(userRepository.findById(comic.getAuthorId()))
                .thenReturn(Optional.of(author));

        AuthorComicAppealRequest request = AuthorComicAppealRequest.builder()
                .category(" ")
                .reason("Please review this moderation result.")
                .build();

        service.submitAppeal(comic.getId(), comic.getAuthorId(), request);

        verify(auditLogService).log(
                eq("COMIC_APPEAL"),
                contains("(Category: GENERAL)")
        );
        verify(notificationService).notifyRoles(
                eq(List.of("MODERATOR", "ADMIN")),
                anyString(),
                contains("Author author-user"),
                eq("APPEAL"),
                eq(NotificationPreferenceKey.REVIEW_QUEUE),
                anyString()
        );
    }

    @Test
    void submitAppealStillSucceedsWhenCacheEvictionFails() {
        ComicEntity comic = ownedComic(ComicModerationStatus.REJECTED);
        stubOwnedComic(comic);
        when(userRepository.findById(comic.getAuthorId()))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("cache unavailable"))
                .when(comicCrudPlugin)
                .evictComicCache(comic.getId());

        AuthorComicAppealRequest request = AuthorComicAppealRequest.builder()
                .category("GENERAL")
                .reason("Please review this moderation result.")
                .build();

        assertDoesNotThrow(
                () -> service.submitAppeal(
                        comic.getId(),
                        comic.getAuthorId(),
                        request
                )
        );

        verify(notificationService).notifyRoles(
                eq(List.of("MODERATOR", "ADMIN")),
                anyString(),
                contains("Author Author submitted"),
                eq("APPEAL"),
                eq(NotificationPreferenceKey.REVIEW_QUEUE),
                anyString()
        );
    }

    @Test
    void submitAppealWrapsUnexpectedFailureAsInternalServerError() {
        ComicEntity comic = ownedComic(ComicModerationStatus.REJECTED);
        stubOwnedComic(comic);
        when(comicRepository.save(comic))
                .thenThrow(new RuntimeException("database unavailable"));

        AuthorComicAppealRequest request = AuthorComicAppealRequest.builder()
                .category("GENERAL")
                .reason("Please review this moderation result.")
                .build();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitAppeal(
                        comic.getId(),
                        comic.getAuthorId(),
                        request
                )
        );

        assertEquals(500, error.getCode());
        assertTrue(error.getMessage().contains("submitAppeal Error"));
        verifyNoInteractions(notificationService, auditLogService);
    }

    // ===== helpers =====

    private void stubCreateSave(UUID comicId) {
        when(comicRepository.save(any(ComicEntity.class))).thenAnswer(invocation -> {
            ComicEntity saved = invocation.getArgument(0);
            saved.setId(comicId);
            return saved;
        });
        when(chapterRepository.countByComic_IdAndDeletedFalse(comicId))
                .thenReturn(0L);
    }

    private AuthorComicCreateRequest createRequest(UUID authorId) {
        AuthorComicCreateRequest request = new AuthorComicCreateRequest();
        request.setAuthorId(authorId);
        request.setTitle("  My Comic  ");
        request.setSummary("  A story summary  ");
        request.setLanguage("  English  ");
        request.setMinimumAge(13);
        request.setCover("  https://cdn.example/cover.jpg  ");
        request.setGenres(List.of("Action"));
        request.setPublicationStatus(ComicPublicationStatus.ONGOING);
        return request;
    }

    private AuthorComicUpdateRequest updateRequest(UUID authorId) {
        AuthorComicUpdateRequest request = new AuthorComicUpdateRequest();
        request.setAuthorId(authorId);
        request.setTitle("My Comic");
        request.setSummary("A story summary");
        request.setLanguage("English");
        request.setMinimumAge(13);
        request.setCover("https://cdn.example/cover.jpg");
        request.setGenres(List.of("Action"));
        request.setPublicationStatus(ComicPublicationStatus.ONGOING);
        return request;
    }

    private ComicEntity ownedComic(ComicModerationStatus moderationStatus) {
        ComicEntity comic = ComicEntity.builder()
                .authorId(UUID.randomUUID())
                .title("My Comic")
                .summary("A story summary")
                .language("English")
                .minimumAge(13)
                .cover("https://cdn.example/cover.jpg")
                .publicationStatus(ComicPublicationStatus.ONGOING)
                .moderationStatus(moderationStatus)
                .genres(Set.of(defaultGenre))
                .chapterCount(1)
                .build();
        comic.setId(UUID.randomUUID());
        comic.setDeleted(false);
        return comic;
    }

    private void stubOwnedComic(ComicEntity comic) {
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(
                comic.getId(),
                comic.getAuthorId()
        )).thenReturn(Optional.of(comic));
    }

    private void stubUpdateNoPending(ComicEntity comic) {
        stubOwnedComic(comic);
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of());
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
    }

    private void stubPendingComicEdit(
            ComicEntity comic,
            SubmissionEntity pending
    ) {
        stubOwnedComic(comic);
        when(submissionRepository
                .findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comic.getId(), comic.getAuthorId(), "author", "pending"
                )).thenReturn(List.of(pending));
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
                .thenReturn(1L);
    }

    private SubmissionEntity pendingComicSubmission(ComicEntity comic) {
        return SubmissionEntity.builder()
                .comicId(comic.getId())
                .authorId(comic.getAuthorId())
                .queueType("author")
                .status("pending")
                .build();
    }

    private GenreEntity genre(String name, String slug) {
        GenreEntity genre = new GenreEntity();
        genre.setId(UUID.randomUUID());
        genre.setName(name);
        genre.setSlug(slug);
        return genre;
    }

    private ComicChapterCountProjection chapterCountProjection(
            UUID comicId,
            Long chapterCount
    ) {
        ComicChapterCountProjection projection = mock(ComicChapterCountProjection.class);
        when(projection.getComicId()).thenReturn(comicId);
        when(projection.getChapterCount()).thenReturn(chapterCount);
        return projection;
    }

    private void assertCancelled(SubmissionEntity submission) {
        assertEquals("cancelled", submission.getStatus());
        assertEquals(Boolean.TRUE, submission.getDeleted());
    }
}
