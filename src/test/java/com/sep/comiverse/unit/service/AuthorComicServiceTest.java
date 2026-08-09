package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.service.AuthorComicService;
import com.sep.comiverse.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorComicServiceTest {

    @Mock
    private IComicRepository comicRepository;
    @Mock
    private IGenreRepository genreRepository;
    @Mock
    private IChapterRepository chapterRepository;
    @Mock
    private ISubmissionRepository submissionRepository;
    @Mock
    private IComicMetricSnapshotRepository metricSnapshotRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.sep.comiverse.repository.IUserRepository userRepository;
    @Mock
    private com.sep.comiverse.service.AuditLogService auditLogService;
    @Mock
    private com.sep.comiverse.plugin.crud.ComicCrudPlugin comicCrudPlugin;

    private AuthorComicService service;

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
                comicCrudPlugin
        );
    }

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
        ComicEntity saved = captor.getValue();
        assertEquals(authorId, saved.getAuthorId());
        assertEquals("My Comic", saved.getTitle());
        assertEquals("English", saved.getLanguage());
        assertEquals(13, saved.getMinimumAge());
        assertEquals("https://cdn.example/cover.jpg", saved.getCover());
        assertEquals(ComicPublicationStatus.ONGOING, saved.getPublicationStatus());
        assertEquals(ComicModerationStatus.DRAFT, saved.getModerationStatus());
        assertEquals(0, saved.getChapterCount());
        assertEquals(comicId, response.getId());
        assertEquals(0, response.getChapterCount());
    }

    @Test
    void createComicRejectsMissingRequiredContentBeforeRepositoryCalls() {
        AuthorComicCreateRequest request = createRequest(UUID.randomUUID());
        request.setCover("   ");

        CustomException error = assertThrows(CustomException.class, () -> service.createComic(request));

        assertEquals("Cover image is required", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        verifyNoInteractions(comicRepository, genreRepository, chapterRepository, submissionRepository);
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

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
        assertEquals("Comic not found or does not belong to this author", error.getMessage());
    }

    @Test
    void submitForReviewRequiresAtLeastOneChapter() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(comic.getId(), comic.getAuthorId()))
                .thenReturn(Optional.of(comic));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(0L);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals("Comic must have at least one chapter before it can be submitted for review", error.getMessage());
        verify(submissionRepository, never()).save(any());
        verify(notificationService, never()).notifyModeratorsWithLanguage(any(), any(), any(), any(), any());
    }

    @Test
    void submitForReviewCreatesModeratorQueueItemAndNotification() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(comic.getId(), comic.getAuthorId()))
                .thenReturn(Optional.of(comic));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);
        when(submissionRepository.findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                comic.getId(), comic.getAuthorId(), "author"
        )).thenReturn(Optional.empty());
        when(comicRepository.save(comic)).thenReturn(comic);

        AuthorComicResponse response = service.submitForReview(comic.getId(), comic.getAuthorId());

        ArgumentCaptor<SubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(SubmissionEntity.class);
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
    void submitForReviewRejectsDuplicatePendingSubmission() {
        ComicEntity comic = ownedComic(ComicModerationStatus.DRAFT);
        SubmissionEntity pending = SubmissionEntity.builder().status("pending").build();
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(comic.getId(), comic.getAuthorId()))
                .thenReturn(Optional.of(comic));
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);
        when(submissionRepository.findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                comic.getId(), comic.getAuthorId(), "author"
        )).thenReturn(Optional.of(pending));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.submitForReview(comic.getId(), comic.getAuthorId())
        );

        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals("Comic has already been submitted for review", error.getMessage());
        verify(submissionRepository, never()).save(any());
        verify(comicRepository, never()).save(any());
    }

    @Test
    void editingModeratedContentCancelsPendingProfileSubmissionAndReturnsToDraft() {
        ComicEntity comic = ownedComic(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        SubmissionEntity pending = SubmissionEntity.builder().status("pending").build();
        when(comicRepository.findByIdAndAuthorIdAndDeletedFalse(comic.getId(), comic.getAuthorId()))
                .thenReturn(Optional.of(comic));
        when(submissionRepository.findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                comic.getId(), comic.getAuthorId(), "author", "pending"
        )).thenReturn(List.of(pending));
        when(comicRepository.save(comic)).thenReturn(comic);
        when(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId())).thenReturn(1L);
        AuthorComicUpdateRequest request = new AuthorComicUpdateRequest();
        request.setAuthorId(comic.getAuthorId());
        request.setTitle("  Updated Comic Title  ");

        AuthorComicResponse response = service.updateComic(comic.getId(), request);

        assertEquals("Updated Comic Title", comic.getTitle());
        assertEquals(ComicModerationStatus.DRAFT, comic.getModerationStatus());
        assertEquals("cancelled", pending.getStatus());
        assertEquals(Boolean.TRUE, pending.getDeleted());
        assertEquals(ComicModerationStatus.DRAFT, response.getModerationStatus());
        verify(submissionRepository).save(pending);
        verify(comicRepository).save(comic);
    }

    private AuthorComicCreateRequest createRequest(UUID authorId) {
        AuthorComicCreateRequest request = new AuthorComicCreateRequest();
        request.setAuthorId(authorId);
        request.setTitle("  My Comic  ");
        request.setSummary("  A story summary  ");
        request.setLanguage("  English  ");
        request.setMinimumAge(null);
        request.setCover("  https://cdn.example/cover.jpg  ");
        request.setGenres(List.of());
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
                .genres(Set.of())
                .chapterCount(1)
                .build();
        comic.setId(UUID.randomUUID());
        comic.setDeleted(false);
        return comic;
    }
}
