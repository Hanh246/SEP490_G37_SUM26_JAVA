package com.sep.comiverse.service;

import com.sep.comiverse.constants.ComicStatus;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ComicMetricsResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.SubmissionEntity;
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

    @Transactional
    public AuthorComicResponse createComic(AuthorComicCreateRequest request) {
        validateCreateRequest(request);

        String slug = normalizeSlug(StringUtils.hasText(request.getSlug()) ? request.getSlug() : request.getTitle());
        if (comicRepository.existsBySlugAndDeletedFalse(slug)) {
            throw new CustomException(409, "Slug already exists", HttpStatus.CONFLICT);
        }

        String coverImageUrl = trimToNull(request.getCoverImageUrl());
        Set<GenreEntity> genres = resolveGenres(request.getGenres());

        ComicEntity comic = ComicEntity.builder()
                .authorId(request.getAuthorId())
                .title(request.getTitle().trim())
                .slug(slug)
                .summary(trimToNull(request.getDescription()))
                .minimumAge(normalizeMinimumAge(request.getMinimumAge()))
                .cover(coverImageUrl)
                .thumbnail(coverImageUrl)
                .status(normalizePublicationStatus(request.getPublicationStatus()))
                .publicationStatus(normalizePublicationStatusEntity(request.getPublicationStatus()))
                .moderationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW)
                .genres(genres)
                .genreIds(genres.stream().map(GenreEntity::getId).toList())
                .viewCount(0L)
                .saveCount(0)
                .likeCount(0)
                .ratingAverage(0.0)
                .ratingCount(0)
                .chapterCount(0)
                .lastChapterUpdatedAt(Instant.now())
                .build();

        ComicEntity savedComic = comicRepository.save(comic);
        createComicProfileReviewSubmission(savedComic);
        return toComicResponse(savedComic);
    }

    @Transactional(readOnly = true)
    public AuthorComicResponse getComic(UUID comicId, UUID authorId) {
        return toComicResponse(getOwnedComic(comicId, authorId));
    }

    @Transactional(readOnly = true)
    public Page<AuthorComicResponse> listOwnComics(UUID authorId, PaginationSearchDTO pagination) {
        if (authorId == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        return comicRepository.findAuthorComics(authorId, safePagination.getSearch(), safePagination.toPageRequest())
                .map(this::toComicResponse);
    }

    @Transactional
    public AuthorComicResponse updateComic(UUID comicId, AuthorComicUpdateRequest request) {
        if (request == null || request.getAuthorId() == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }

        ComicEntity comic = getOwnedComic(comicId, request.getAuthorId());

        boolean requiresModerationReview = false;

        if (StringUtils.hasText(request.getSlug())) {
            String slug = normalizeSlug(request.getSlug());
            boolean duplicateSlug = comicRepository.existsBySlugAndDeletedFalse(slug) && !slug.equals(comic.getSlug());
            if (duplicateSlug) {
                throw new CustomException(409, "Slug already exists", HttpStatus.CONFLICT);
            }
            requiresModerationReview |= !slug.equals(comic.getSlug());
            comic.setSlug(slug);
        }

        if (StringUtils.hasText(request.getTitle())) {
            String title = request.getTitle().trim();
            requiresModerationReview |= differentString(comic.getTitle(), title);
            comic.setTitle(title);
        }
        if (request.getDescription() != null) {
            String summary = trimToNull(request.getDescription());
            requiresModerationReview |= differentString(comic.getSummary(), summary);
            comic.setSummary(summary);
        }
        if (request.getMinimumAge() != null) {
            Integer minimumAge = normalizeMinimumAge(request.getMinimumAge());
            requiresModerationReview |= differentInteger(comic.getMinimumAge(), minimumAge);
            comic.setMinimumAge(minimumAge);
        }
        if (request.getCoverImageUrl() != null) {
            String coverImageUrl = trimToNull(request.getCoverImageUrl());
            requiresModerationReview |= differentString(comic.getCover(), coverImageUrl) || differentString(comic.getThumbnail(), coverImageUrl);
            comic.setCover(coverImageUrl);
            comic.setThumbnail(coverImageUrl);
        }
        if (request.getGenres() != null) {
            Set<GenreEntity> genres = resolveGenres(request.getGenres());
            comic.setGenres(genres);
            comic.setGenreIds(genres.stream().map(GenreEntity::getId).toList());
        }
        if (StringUtils.hasText(request.getPublicationStatus())) {
            comic.setStatus(normalizePublicationStatus(request.getPublicationStatus()));
            comic.setPublicationStatus(normalizePublicationStatusEntity(request.getPublicationStatus()));
        }

        if (requiresModerationReview) {
            comic.setModerationStatus(ComicModerationStatus.SUBMITTED_FOR_REVIEW);
            if (!hasPendingComicProfileSubmission(comic)) {
                createComicProfileReviewSubmission(comic);
            }
        }
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
                .orElseThrow(() -> new CustomException(404, "Comic not found or does not belong to this author", HttpStatus.NOT_FOUND));
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
                .cover(firstNonBlank(comic.getThumbnail(), comic.getCover()))
                .content("Comic profile is waiting for moderator review.")
                .build());
    }

    private AuthorComicResponse toComicResponse(ComicEntity comic) {
        ComicModerationStatus moderationStatus = resolveComicProfileModerationStatus(comic);
        return AuthorComicResponse.builder()
                .id(comic.getId())
                .authorId(comic.getAuthorId())
                .title(comic.getTitle())
                .slug(comic.getSlug())
                .description(comic.getSummary())
                .minimumAge(comic.getMinimumAge())
                .coverImageUrl(firstNonBlank(comic.getThumbnail(), comic.getCover()))
                .genres(toGenreNames(comic.getGenres()))
                .publicationStatus(formatPublicationStatus(comic.getStatus()))
                .status(moderationStatus)
                .moderationStatus(moderationStatus)
                .moderationNote(resolveComicProfileModerationNote(comic))
                .chapters(countChapters(comic.getId()))
                .views(String.valueOf(defaultLong(comic.getViewCount())))
                .publishedAt(moderationStatus == ComicModerationStatus.PUBLISHED ? toDate(comic.getCreatedAt()) : null)
                .createdAt(toDate(comic.getCreatedAt()))
                .updatedAt(toDate(comic.getUpdatedAt()))
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

    private ComicPublicationStatus normalizePublicationStatusEntity(String value) {
        ComicStatus normalized = normalizePublicationStatus(value);
        return switch (normalized) {
            case ONGOING -> ComicPublicationStatus.ONGOING;
            case COMPLETED -> ComicPublicationStatus.COMPLETED;
            case PAUSED -> ComicPublicationStatus.HIATUS;
            case ARCHIVED -> ComicPublicationStatus.CANCEL;
        };
    }

    private String normalizeSlug(String rawSlug) {
        if (!StringUtils.hasText(rawSlug)) {
            throw new CustomException(400, "Slug is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = Normalizer.normalize(rawSlug.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (!StringUtils.hasText(normalized)) {
            throw new CustomException(400, "Slug is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private ComicStatus normalizePublicationStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return ComicStatus.ONGOING;
        }
        String normalized = value.trim().replace(" ", "_").replace("-", "_").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ONGOING", "ON_GOING" -> ComicStatus.ONGOING;
            case "COMPLETED", "COMPLETE" -> ComicStatus.COMPLETED;
            case "PAUSED", "HIATUS" -> ComicStatus.PAUSED;
            case "ARCHIVED", "CANCEL", "CANCELLED" -> ComicStatus.ARCHIVED;
            default -> throw new CustomException(400, "Publication status must be Ongoing, Completed, Paused/Hiatus, or Archived", HttpStatus.BAD_REQUEST);
        };
    }

    private String formatPublicationStatus(ComicStatus status) {
        if (status == null) {
            return "Ongoing";
        }
        return switch (status) {
            case ONGOING -> "Ongoing";
            case COMPLETED -> "Completed";
            case PAUSED -> "Hiatus";
            case ARCHIVED -> "Archived";
        };
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
        genre.setSlug(normalizeSlug(name));
        return genreRepository.save(genre);
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

    private String resolveComicProfileModerationNote(ComicEntity comic) {
        return findLatestComicProfileSubmission(comic)
                .map(SubmissionEntity::getRejectionReason)
                .orElse(null);
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

    private Integer countChapters(UUID comicId) {
        if (comicId == null) {
            return 0;
        }
        long count = chapterRepository.countByComic_IdAndDeletedFalse(comicId);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
