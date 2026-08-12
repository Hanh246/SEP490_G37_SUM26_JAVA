package com.sep.comiverse.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ComicMetricsResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.projection.ComicChapterCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorComicService {

    private final IComicRepository comicRepository;
    private final IGenreRepository genreRepository;
    private final IChapterRepository chapterRepository;
    private final ISubmissionRepository submissionRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;
    private final NotificationService notificationService;
    private final com.sep.comiverse.repository.IUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final com.sep.comiverse.plugin.crud.ComicCrudPlugin comicCrudPlugin;
    private final AuthorLicenseService authorLicenseService;

    @Transactional
    public void confirmModEdit(UUID comicId, UUID authorId) {
        ComicEntity comic = getOwnedComic(comicId, authorId);
        comic.setIsModEdited(false);
        comic.setPreviousStateSnapshot(null);
        comicRepository.save(comic);
        
        try {
            if (comicCrudPlugin != null) comicCrudPlugin.evictComicCache(comicId);
        } catch (Exception e) {}
        
        auditLogService.log("COMIC_AUTHOR", "Author confirmed moderator edit for comic " + comicId);
    }

    @Transactional
    public void submitAppeal(UUID comicId, UUID authorId, com.sep.comiverse.dto.request.AuthorComicAppealRequest request) {
        try {
            if (request == null || !StringUtils.hasText(request.getReason())) {
                throw new CustomException(400, "Appeal statement cannot be blank", HttpStatus.BAD_REQUEST);
            }
            ComicEntity comic = getOwnedComic(comicId, authorId);
            String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : "GENERAL";
            String reason = request.getReason().trim();

            comic.setModerationStatus(ComicModerationStatus.UNPUBLISHED);
            comic.setIsAppealed(true);
            comic.setAppealReason(reason);
            comicRepository.save(comic);
            
            try {
                if (comicCrudPlugin != null) comicCrudPlugin.evictComicCache(comicId);
            } catch (Exception e) {}

            String authorName = userRepository.findById(authorId)
                    .map(u -> StringUtils.hasText(u.getFullName()) ? u.getFullName() : u.getUsername())
                    .orElse("Author");

            auditLogService.log("COMIC_APPEAL",
                    "Author " + authorName + " submitted appeal for comic \"" + comic.getTitle() + "\" (Category: " + category + "): " + reason);

            String formattedCategory = category != null ? java.util.Arrays.stream(category.replace("_", " ").toLowerCase().split(" "))
                    .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(java.util.stream.Collectors.joining(" ")) : "Other";
            
            String notifTitle = "Author Appeal: " + comic.getTitle();
            String notifMessage = "Author " + authorName + " submitted a moderation appeal for \"" + comic.getTitle() + "\" [" + formattedCategory + "]: " + reason;

            notificationService.notifyRoles(
                    List.of("MODERATOR", "ADMIN"),
                    notifTitle,
                    notifMessage,
                    "APPEAL",
                    NotificationPreferenceKey.REVIEW_QUEUE,
                    "/moderator/comic/" + comic.getId()
            );
        } catch (CustomException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new CustomException(500, "submitAppeal Error: " + t.getClass().getSimpleName() + " - " + t.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public AuthorComicResponse createComic(AuthorComicCreateRequest request) {
        validateCreateRequest(request);
        authorLicenseService.assertPublishingAllowed(request.getAuthorId());

        Set<GenreEntity> genres = resolveGenres(request.getGenres());
        ComicPublicationStatus publicationStatus = request.getPublicationStatus() == null
                ? ComicPublicationStatus.ONGOING
                : request.getPublicationStatus();

        ComicEntity comic = ComicEntity.builder()
                .authorId(request.getAuthorId())
                .title(request.getTitle().trim())
                .summary(trimToNull(request.getSummary()))
                .language(normalizeRequiredLanguage(request.getLanguage()))
                .minimumAge(normalizeMinimumAge(request.getMinimumAge()))
                .cover(trimToNull(request.getCover()))
                .publicationStatus(publicationStatus)
                .moderationStatus(ComicModerationStatus.DRAFT)
                .genres(genres)
                .viewCount(0L)
                .saveCount(0)
                .likeCount(0)
                .ratingAverage(0.0)
                .ratingCount(0)
                .chapterCount(0)
                .lastChapterUpdatedAt(null)
                .build();

        return toComicResponse(comicRepository.save(comic));
    }

    @Transactional(readOnly = true)
    public AuthorComicResponse getComic(UUID comicId, UUID authorId) {
        return toComicResponse(getOwnedComic(comicId, authorId));
    }

    @Transactional(readOnly = true)
    public Page<AuthorComicResponse> listOwnComics(
            UUID authorId,
            PaginationSearchDTO pagination
    ) {
        if (authorId == null) {
            throw new CustomException(
                    400,
                    "Author id is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        PaginationSearchDTO safePagination =
                pagination != null
                        ? pagination
                        : new PaginationSearchDTO();

        String search = safePagination.getSearch();

        // Fast path (no JOIN on genres, no DISTINCT, no correlated subquery) for the
        // common case where the author just opens the list without a search keyword.
        // The full search query is only used when a keyword is actually provided.
        Page<ComicEntity> comicPage = StringUtils.hasText(search)
                ? comicRepository.searchAuthorComics(authorId, search, safePagination.toPageRequest())
                : comicRepository.findByAuthorIdAndDeletedFalse(authorId, safePagination.toPageRequest());

        Map<UUID, Integer> chapterCountsByComicId = loadChapterCountsByComicId(comicPage.getContent());

        return comicPage.map(comic -> toComicSummaryResponse(
                comic,
                chapterCountsByComicId.getOrDefault(comic.getId(), 0)
        ));
    }
    @Transactional
    public AuthorComicResponse updateComic(UUID comicId, AuthorComicUpdateRequest request) {
        if (request == null || request.getAuthorId() == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }

        ComicEntity comic = getOwnedComic(comicId, request.getAuthorId());
        boolean requiresModerationReview = false;

        if (StringUtils.hasText(request.getTitle())) {
            String title = request.getTitle().trim();
            requiresModerationReview |= differentString(comic.getTitle(), title);
            comic.setTitle(title);
        }
        if (request.getSummary() != null) {
            String summary = trimToNull(request.getSummary());
            requiresModerationReview |= differentString(comic.getSummary(), summary);
            comic.setSummary(summary);
        }
        if (request.getLanguage() != null) {
            String language = normalizeRequiredLanguage(request.getLanguage());
            requiresModerationReview |= differentString(comic.getLanguage(), language);
            comic.setLanguage(language);
        }
        if (request.getMinimumAge() != null) {
            Integer minimumAge = normalizeMinimumAge(request.getMinimumAge());
            requiresModerationReview |= differentInteger(comic.getMinimumAge(), minimumAge);
            comic.setMinimumAge(minimumAge);
        }
        if (request.getCover() != null) {
            String cover = trimToNull(request.getCover());
            requiresModerationReview |= differentString(comic.getCover(), cover);
            comic.setCover(cover);
        }
        if (request.getGenres() != null) {
            Set<GenreEntity> genres = resolveGenres(request.getGenres());
            Set<UUID> currentGenreIds = comic.getGenres() == null
                    ? Set.of()
                    : comic.getGenres().stream().map(GenreEntity::getId).collect(Collectors.toSet());
            Set<UUID> requestedGenreIds = genres.stream()
                    .map(GenreEntity::getId)
                    .collect(Collectors.toSet());
            requiresModerationReview |= !currentGenreIds.equals(requestedGenreIds);
            comic.setGenres(genres);
        }
        if (request.getPublicationStatus() != null) {
            comic.setPublicationStatus(request.getPublicationStatus());
        }

        if (requiresModerationReview) {
            // Any profile content change invalidates the currently pending/approved
            // moderation result. Cancel only the Author profile submission; chapter
            // submissions are independent and remain untouched.
            cancelPendingComicSubmissions(comicId, request.getAuthorId());
            comic.setModerationStatus(ComicModerationStatus.DRAFT);
        }

        return toComicResponse(comicRepository.save(comic));
    }

    @Transactional
    public AuthorComicResponse submitForReview(UUID comicId, UUID authorId) {
        authorLicenseService.assertPublishingAllowed(authorId);
        ComicEntity comic = getOwnedComic(comicId, authorId);

        long chapterCount = chapterRepository.countByComic_IdAndDeletedFalse(comicId);
        if (chapterCount < 1) {
            throw new CustomException(
                    400,
                    "Comic must have at least one chapter before it can be submitted for review",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (comic.getModerationStatus() == ComicModerationStatus.PUBLISHED) {
            throw new CustomException(409, "Published comics cannot be submitted again", HttpStatus.CONFLICT);
        }
        if (hasPendingComicProfileSubmission(comic)
                || comic.getModerationStatus() == ComicModerationStatus.SUBMITTED_FOR_REVIEW) {
            throw new CustomException(409, "Comic has already been submitted for review", HttpStatus.CONFLICT);
        }

        createComicProfileReviewSubmission(comic);
        comic.setModerationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
        return toComicResponse(comicRepository.save(comic));
    }

    @Transactional
    public void deleteComic(UUID comicId, UUID authorId) {
        ComicEntity comic = getOwnedComic(comicId, authorId);
        chapterRepository.findAllByComic_IdAndDeletedFalse(comicId).forEach(chapter -> {
            chapter.setDeleted(true);
            chapterRepository.save(chapter);
            cancelPendingChapterSubmissions(chapter.getId(), authorId);
        });
        cancelPendingComicSubmissions(comicId, authorId);
        comic.setDeleted(true);
        comicRepository.save(comic);
    }

    @Transactional
    public void revokeComicProfileSubmissionIfEmpty(UUID comicId, UUID authorId) {
        long chapterCount = chapterRepository.countByComic_IdAndDeletedFalse(comicId);
        if (chapterCount < 1) {
            ComicEntity comic = getOwnedComic(comicId, authorId);
            if (comic.getModerationStatus() == ComicModerationStatus.SUBMITTED_FOR_REVIEW) {
                cancelPendingComicSubmissions(comicId, authorId);
                comic.setModerationStatus(ComicModerationStatus.DRAFT);
                comicRepository.save(comic);
            }
        }
    }

    @Transactional(readOnly = true)
    public ComicMetricsResponse getComicMetrics(UUID comicId, UUID authorId) {
        ComicEntity comic = getOwnedComic(comicId, authorId);
        ComicMetricSnapshotEntity snapshot = metricSnapshotRepository
                .findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(comicId, authorId)
                .orElse(null);

        long savedCount = defaultInteger(comic.getSaveCount()).longValue();
        return ComicMetricsResponse.builder()
                .comicId(comicId)
                .authorId(authorId)
                .viewCount(defaultLong(comic.getViewCount()))
                .followCount(savedCount)
                .favoriteCount(savedCount)
                .likeCount(defaultInteger(comic.getLikeCount()).longValue())
                .chapterCount(Math.toIntExact(chapterRepository.countByComic_IdAndDeletedFalse(comicId)))
                .ratingAverage(comic.getRatingAverage() == null ? 0.0 : comic.getRatingAverage())
                .ratingCount(defaultInteger(comic.getRatingCount()))
                .estimatedRevenue(snapshot == null || snapshot.getEstimatedRevenue() == null
                        ? BigDecimal.ZERO
                        : snapshot.getEstimatedRevenue())
                .snapshotAt(snapshot == null ? new Date() : toDate(snapshot.getCreatedAt()))
                .build();
    }

    @Transactional(readOnly = true)
    public ComicEntity getOwnedComic(UUID comicId, UUID authorId) {
        if (comicId == null || authorId == null) {
            throw new CustomException(400, "Comic id and author id are required", HttpStatus.BAD_REQUEST);
        }

        return comicRepository.findByIdAndAuthorIdAndDeletedFalse(comicId, authorId)
                .orElseThrow(() -> new CustomException(
                        404,
                        "Comic not found or does not belong to this author",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void cancelPendingComicSubmissions(UUID comicId, UUID authorId) {
        submissionRepository.findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        comicId, authorId, "author", "pending")
                .forEach(submission -> {
                    submission.setStatus("cancelled");
                    submission.setDeleted(true);
                    submissionRepository.save(submission);
                });
    }

    private void cancelPendingChapterSubmissions(UUID chapterId, UUID authorId) {
        submissionRepository.findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        chapterId, authorId, "author", "pending")
                .forEach(submission -> {
                    submission.setStatus("cancelled");
                    submission.setDeleted(true);
                    submissionRepository.save(submission);
                });
    }

    private void createComicProfileReviewSubmission(ComicEntity comic) {
        Date now = new Date();
        submissionRepository.save(SubmissionEntity.builder()
                .comicId(comic.getId())
                .chapterId(null)
                .authorId(comic.getAuthorId())
                .title(comic.getTitle())
                .chapter("Comic profile")
                .submittedBy("Author: " + comic.getAuthorId())
                .queueType("author")
                .timeLabel("Just now")
                .timestamp(now.getTime())
                .words(0)
                .priority("Medium")
                .flags(0)
                .status("pending")
                .cover(comic.getCover())
                .content("Comic profile is waiting for moderator review.")
                .build());
        notificationService.notifyModeratorsWithLanguage(
                comic.getLanguage(),
                "New comic review",
                comic.getTitle() + " was submitted by an author for moderation.",
                "UPDATE",
                NotificationPreferenceKey.REVIEW_QUEUE
        );
    }

    private AuthorComicResponse toComicResponse(ComicEntity comic) {
        return buildComicResponse(comic, resolveComicProfileModerationStatus(comic));
    }

    /**
     * Lightweight mapper for the author list. It avoids the submission lookup
     * performed by the detail mapper and uses only fields mapped by ComicEntity.
     */
    private AuthorComicResponse toComicSummaryResponse(
            ComicEntity comic,
            int chapterCount
    ) {
        ComicModerationStatus moderationStatus = comic.getModerationStatus() == null
                ? ComicModerationStatus.DRAFT
                : comic.getModerationStatus();

        return buildComicResponse(
                comic,
                moderationStatus,
                toGenreNames(comic.getGenres()),
                chapterCount
        );
    }

    private AuthorComicResponse buildComicResponse(
            ComicEntity comic,
            ComicModerationStatus moderationStatus
    ) {
        return buildComicResponse(
                comic,
                moderationStatus,
                toGenreNames(comic.getGenres()),
                Math.toIntExact(chapterRepository.countByComic_IdAndDeletedFalse(comic.getId()))
        );
    }

    private AuthorComicResponse buildComicResponse(
            ComicEntity comic,
            ComicModerationStatus moderationStatus,
            List<String> genreNames,
            int chapterCount
    ) {
        return AuthorComicResponse.builder()
                .id(comic.getId())
                .authorId(comic.getAuthorId())
                .title(comic.getTitle())
                .summary(comic.getSummary())
                .language(comic.getLanguage())
                .minimumAge(comic.getMinimumAge())
                .cover(comic.getCover())
                .genres(genreNames)
                .publicationStatus(comic.getPublicationStatus())
                .moderationStatus(moderationStatus)
                .isAppealed(comic.getIsAppealed() != null && comic.getIsAppealed())
                .appealReason(comic.getAppealReason())
                .rejectionReason(comic.getRejectionReason())
                .viewCount(defaultLong(comic.getViewCount()))
                .saveCount(defaultInteger(comic.getSaveCount()))
                .likeCount(defaultInteger(comic.getLikeCount()))
                .ratingAverage(comic.getRatingAverage() == null ? 0.0 : comic.getRatingAverage())
                .ratingCount(defaultInteger(comic.getRatingCount()))
                .latestChapterNumber(comic.getLatestChapterNumber())
                .lastChapterUpdatedAt(comic.getLastChapterUpdatedAt())
                .chapterCount(chapterCount)
                .createdAt(comic.getCreatedAt())
                .updatedAt(comic.getUpdatedAt())
                .isModEdited(comic.getIsModEdited())
                .previousStateSnapshot(comic.getPreviousStateSnapshot())
                .build();
    }

    private void validateCreateRequest(AuthorComicCreateRequest request) {
        if (request == null) {
            throw new CustomException(400, "Request body is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getAuthorId() == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new CustomException(400, "Title is required", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.getLanguage())) {
            throw new CustomException(400, "Comic language is required", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.getCover())) {
            throw new CustomException(400, "Cover image is required", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeRequiredLanguage(String language) {
        String normalized = trimToNull(language);
        if (normalized == null) {
            throw new CustomException(400, "Comic language is required", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 100) {
            throw new CustomException(400, "Comic language must not exceed 100 characters", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Integer normalizeMinimumAge(Integer minimumAge) {
        if (minimumAge == null) {
            return 13;
        }
        if (minimumAge < 0 || minimumAge > 21) {
            throw new CustomException(400, "Minimum age must be between 0 and 21", HttpStatus.BAD_REQUEST);
        }
        return minimumAge;
    }

    private Set<GenreEntity> resolveGenres(List<String> genreNames) {
        if (genreNames == null || genreNames.isEmpty()) {
            return new HashSet<>();
        }

        List<GenreEntity> existingGenres = genreRepository.findAll();
        return genreNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(name -> existingGenres.stream()
                        .filter(genre -> genre.getName() != null && genre.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElseGet(() -> createGenre(name)))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private GenreEntity createGenre(String name) {
        GenreEntity genre = new GenreEntity();
        genre.setName(name);
        genre.setSlug(normalizeGenreSlug(name));
        return genreRepository.save(genre);
    }

    private String normalizeGenreSlug(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (!StringUtils.hasText(normalized)) {
            throw new CustomException(400, "Genre name is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Map<UUID, Integer> loadChapterCountsByComicId(List<ComicEntity> comics) {
        if (comics == null || comics.isEmpty()) {
            return Map.of();
        }

        List<UUID> comicIds = comics.stream()
                .map(ComicEntity::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (comicIds.isEmpty()) {
            return Map.of();
        }

        return chapterRepository.countActiveChaptersByComicIds(comicIds).stream()
                .filter(item -> item.getComicId() != null)
                .collect(Collectors.toMap(
                        ComicChapterCountProjection::getComicId,
                        item -> item.getChapterCount() == null ? 0 : Math.toIntExact(item.getChapterCount()),
                        (first, ignored) -> first
                ));
    }



    private List<String> toGenreNames(Set<GenreEntity> genres) {
        if (genres == null || genres.isEmpty()) {
            return List.of();
        }
        return genres.stream()
                .filter(genre -> genre.getName() != null)
                .map(GenreEntity::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private ComicModerationStatus resolveComicProfileModerationStatus(ComicEntity comic) {
        if (comic.getModerationStatus() != null) {
            return comic.getModerationStatus();
        }
        return findLatestComicProfileSubmission(comic)
                .map(this::mapSubmissionStatusToComicStatus)
                .orElse(ComicModerationStatus.DRAFT);
    }

    private boolean hasPendingComicProfileSubmission(ComicEntity comic) {
        return findLatestComicProfileSubmission(comic)
                .map(SubmissionEntity::getStatus)
                .filter(StringUtils::hasText)
                .map(status -> status.trim().toLowerCase(Locale.ROOT))
                .filter("pending"::equals)
                .isPresent();
    }

    private java.util.Optional<SubmissionEntity> findLatestComicProfileSubmission(ComicEntity comic) {
        return submissionRepository.findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                comic.getId(), comic.getAuthorId(), "author");
    }

    private ComicModerationStatus mapSubmissionStatusToComicStatus(SubmissionEntity submission) {
        if (submission == null || !StringUtils.hasText(submission.getStatus())) {
            return ComicModerationStatus.DRAFT;
        }
        return switch (submission.getStatus().trim().toLowerCase(Locale.ROOT)) {
            case "pending" -> ComicModerationStatus.SUBMITTED_FOR_REVIEW;
            case "approved" -> ComicModerationStatus.PUBLISHED;
            case "rejected" -> ComicModerationStatus.REJECTED;
            default -> ComicModerationStatus.DRAFT;
        };
    }

    private boolean differentString(String current, String next) {
        return !java.util.Objects.equals(trimToNull(current), trimToNull(next));
    }

    private boolean differentInteger(Integer current, Integer next) {
        return !java.util.Objects.equals(current, next);
    }

    private Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

}
