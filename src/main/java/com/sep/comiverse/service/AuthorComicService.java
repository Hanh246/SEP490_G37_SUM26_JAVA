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
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
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

    @Transactional
    public AuthorComicResponse createComic(AuthorComicCreateRequest request) {
        validateCreateRequest(request);

        Set<GenreEntity> genres = resolveGenres(request.getGenres());
        ComicPublicationStatus publicationStatus = request.getPublicationStatus() == null
                ? ComicPublicationStatus.ONGOING
                : request.getPublicationStatus();

        ComicEntity comic = ComicEntity.builder()
                .authorId(request.getAuthorId())
                .title(request.getTitle().trim())
                .summary(trimToNull(request.getSummary()))
                .minimumAge(normalizeMinimumAge(request.getMinimumAge()))
                .cover(trimToNull(request.getCover()))
                .publicationStatus(publicationStatus)
                .moderationStatus(ComicModerationStatus.DRAFT)
                .genres(genres)
                .genreIds(genres.stream().map(GenreEntity::getId).toList())
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

        Map<UUID, String> genreNamesById = loadGenreNamesById(comicPage.getContent());

        return comicPage.map(comic -> toComicSummaryResponse(comic, genreNamesById));
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
            comic.setGenres(genres);
            comic.setGenreIds(genres.stream().map(GenreEntity::getId).toList());
        }
        if (request.getPublicationStatus() != null) {
            comic.setPublicationStatus(request.getPublicationStatus());
        }

        if (requiresModerationReview
                && comic.getModerationStatus() != ComicModerationStatus.PUBLISHED
                && comic.getModerationStatus() != ComicModerationStatus.SUBMITTED_FOR_REVIEW) {
            comic.setModerationStatus(ComicModerationStatus.DRAFT);
        }

        return toComicResponse(comicRepository.save(comic));
    }

    @Transactional
    public AuthorComicResponse submitForReview(UUID comicId, UUID authorId) {
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

    @Transactional(readOnly = true)
    public ComicMetricsResponse getComicMetrics(UUID comicId, UUID authorId) {
        getOwnedComic(comicId, authorId);
        return metricSnapshotRepository.findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(comicId, authorId)
                .map(this::toMetricsResponse)
                .orElseGet(() -> ComicMetricsResponse.builder()
                        .comicId(comicId)
                        .authorId(authorId)
                        .viewCount(0L)
                        .followCount(0L)
                        .favoriteCount(0L)
                        .likeCount(0L)
                        .estimatedRevenue(BigDecimal.ZERO)
                        .snapshotAt(new Date())
                        .build());
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
        notificationService.notifyRoles(
                List.of("MODERATOR"),
                "New comic review",
                comic.getTitle() + " was submitted by an author for moderation.",
                "UPDATE"
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
            Map<UUID, String> genreNamesById
    ) {
        ComicModerationStatus moderationStatus = comic.getModerationStatus() == null
                ? ComicModerationStatus.DRAFT
                : comic.getModerationStatus();

        return buildComicResponse(
                comic,
                moderationStatus,
                toGenreNames(comic.getGenreIds(), genreNamesById)
        );
    }

    private AuthorComicResponse buildComicResponse(
            ComicEntity comic,
            ComicModerationStatus moderationStatus
    ) {
        return buildComicResponse(comic, moderationStatus, toGenreNames(comic.getGenres()));
    }

    private AuthorComicResponse buildComicResponse(
            ComicEntity comic,
            ComicModerationStatus moderationStatus,
            List<String> genreNames
    ) {
        return AuthorComicResponse.builder()
                .id(comic.getId())
                .authorId(comic.getAuthorId())
                .title(comic.getTitle())
                .summary(comic.getSummary())
                .minimumAge(comic.getMinimumAge())
                .cover(comic.getCover())
                .genres(genreNames)
                .publicationStatus(comic.getPublicationStatus())
                .moderationStatus(moderationStatus)
                .viewCount(defaultLong(comic.getViewCount()))
                .saveCount(defaultInteger(comic.getSaveCount()))
                .likeCount(defaultInteger(comic.getLikeCount()))
                .ratingAverage(comic.getRatingAverage() == null ? 0.0 : comic.getRatingAverage())
                .ratingCount(defaultInteger(comic.getRatingCount()))
                .latestChapterNumber(comic.getLatestChapterNumber())
                .lastChapterUpdatedAt(comic.getLastChapterUpdatedAt())
                .chapterCount(defaultInteger(comic.getChapterCount()))
                .createdAt(comic.getCreatedAt())
                .updatedAt(comic.getUpdatedAt())
                .build();
    }

    private ComicMetricsResponse toMetricsResponse(ComicMetricSnapshotEntity entity) {
        return ComicMetricsResponse.builder()
                .comicId(entity.getComicId())
                .authorId(entity.getAuthorId())
                .viewCount(defaultLong(entity.getViewCount()))
                .followCount(defaultLong(entity.getFollowCount()))
                .favoriteCount(defaultLong(entity.getFavoriteCount()))
                .likeCount(defaultLong(entity.getLikeCount()))
                .estimatedRevenue(entity.getEstimatedRevenue() == null ? BigDecimal.ZERO : entity.getEstimatedRevenue())
                .snapshotAt(toDate(entity.getCreatedAt()))
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
        if (!StringUtils.hasText(request.getCover())) {
            throw new CustomException(400, "Cover image is required", HttpStatus.BAD_REQUEST);
        }
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

    private Map<UUID, String> loadGenreNamesById(List<ComicEntity> comics) {
        if (comics == null || comics.isEmpty()) {
            return Map.of();
        }

        Set<UUID> genreIds = comics.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(comic -> comic.getGenreIds() == null
                        ? java.util.stream.Stream.<UUID>empty()
                        : comic.getGenreIds().stream())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (genreIds.isEmpty()) {
            return Map.of();
        }

        return genreRepository.findAllById(genreIds).stream()
                .filter(genre -> genre.getId() != null && StringUtils.hasText(genre.getName()))
                .collect(Collectors.toMap(
                        GenreEntity::getId,
                        GenreEntity::getName,
                        (first, ignored) -> first
                ));
    }

    private List<String> toGenreNames(
            List<UUID> genreIds,
            Map<UUID, String> genreNamesById
    ) {
        if (genreIds == null || genreIds.isEmpty()
                || genreNamesById == null || genreNamesById.isEmpty()) {
            return List.of();
        }

        return genreIds.stream()
                .map(genreNamesById::get)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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