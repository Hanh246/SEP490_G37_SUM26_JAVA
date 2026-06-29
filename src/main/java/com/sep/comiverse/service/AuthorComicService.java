package com.sep.comiverse.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.AuthorComicUpdateRequest;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ComicMetricsResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.enums.ComicStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.IComicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorComicService {

    private static final String DEFAULT_PUBLICATION_STATUS = "Ongoing";

    private final IComicRepository comicRepository;
    private final IComicMetricSnapshotRepository metricSnapshotRepository;

    @Transactional
    public AuthorComicResponse createComic(AuthorComicCreateRequest request) {
        validateCreateRequest(request);

        String slug = normalizeSlug(StringUtils.hasText(request.getSlug()) ? request.getSlug() : request.getTitle());
        if (comicRepository.existsByAuthorIdAndSlugAndDeletedFalse(request.getAuthorId(), slug)) {
            throw new CustomException(409, "Slug already exists for this author", HttpStatus.CONFLICT);
        }

        String publicationStatus = normalizePublicationStatus(request.getPublicationStatus());
        String coverImageUrl = trimToNull(request.getCoverImageUrl());

        ComicEntity comic = ComicEntity.builder()
                .authorId(request.getAuthorId())
                .title(request.getTitle().trim())
                .slug(slug)
                .description(trimToNull(request.getDescription()))
                .coverImageUrl(coverImageUrl)
                .cover(coverImageUrl)
                .author(request.getAuthorId().toString())
                .projectTeam("-")
                .chapters(0)
                .views("0")
                .status(publicationStatus)
                .moderationStatus(ComicStatus.SUBMITTED_FOR_REVIEW.name())
                .genres(joinGenres(request.getGenres()))
                .build();

        return toComicResponse(comicRepository.save(comic));
    }

    public AuthorComicResponse getComic(UUID comicId, UUID authorId) {
        return toComicResponse(getOwnedComic(comicId, authorId));
    }

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

        if (StringUtils.hasText(request.getSlug())) {
            String slug = normalizeSlug(request.getSlug());
            boolean duplicateSlug = comicRepository.existsByAuthorIdAndSlugAndDeletedFalse(request.getAuthorId(), slug)
                    && !slug.equals(comic.getSlug());
            if (duplicateSlug) {
                throw new CustomException(409, "Slug already exists for this author", HttpStatus.CONFLICT);
            }
            comic.setSlug(slug);
        }

        if (StringUtils.hasText(request.getTitle())) {
            comic.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            comic.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getCoverImageUrl() != null) {
            String coverImageUrl = trimToNull(request.getCoverImageUrl());
            comic.setCoverImageUrl(coverImageUrl);
            comic.setCover(coverImageUrl);
        }
        if (request.getGenres() != null) {
            comic.setGenres(joinGenres(request.getGenres()));
        }
        if (StringUtils.hasText(request.getPublicationStatus())) {
            comic.setStatus(normalizePublicationStatus(request.getPublicationStatus()));
        }

        return toComicResponse(comicRepository.save(comic));
    }

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

    public ComicEntity getOwnedComic(UUID comicId, UUID authorId) {
        if (comicId == null || authorId == null) {
            throw new CustomException(400, "Comic id and author id are required", HttpStatus.BAD_REQUEST);
        }

        return comicRepository.findByIdAndAuthorIdAndDeletedFalse(comicId, authorId)
                .orElseThrow(() -> new CustomException(404, "Comic not found or does not belong to this author", HttpStatus.NOT_FOUND));
    }

    private AuthorComicResponse toComicResponse(ComicEntity comic) {
        return AuthorComicResponse.builder()
                .id(comic.getId())
                .authorId(comic.getAuthorId())
                .title(comic.getTitle())
                .slug(comic.getSlug())
                .description(comic.getDescription())
                .coverImageUrl(firstNonBlank(comic.getCoverImageUrl(), comic.getCover()))
                .genres(splitGenres(comic.getGenres()))
                .publicationStatus(comic.getStatus())
                .status(toComicStatus(comic.getModerationStatus()))
                .moderationNote(comic.getModerationNote())
                .chapters(comic.getChapters() == null ? 0 : comic.getChapters())
                .views(comic.getViews() == null ? "0" : comic.getViews())
                .publishedAt(comic.getPublishedAt())
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
                .snapshotAt(entity.getCreatedAt())
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

    private String normalizeSlug(String rawSlug) {
        if (!StringUtils.hasText(rawSlug)) {
            throw new CustomException(400, "Slug is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = Normalizer.normalize(rawSlug.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (!StringUtils.hasText(normalized)) {
            throw new CustomException(400, "Slug is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizePublicationStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_PUBLICATION_STATUS;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("Paused")) {
            return "Hiatus";
        }
        if (normalized.equalsIgnoreCase("Ongoing")
                || normalized.equalsIgnoreCase("Completed")
                || normalized.equalsIgnoreCase("Hiatus")
                || normalized.equalsIgnoreCase("Archived")) {
            return normalized.substring(0, 1).toUpperCase() + normalized.substring(1).toLowerCase();
        }
        throw new CustomException(400, "Publication status must be Ongoing, Completed, Hiatus, or Archived", HttpStatus.BAD_REQUEST);
    }

    private String joinGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return "";
        }
        return genres.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private List<String> splitGenres(String genres) {
        if (!StringUtils.hasText(genres)) {
            return List.of();
        }
        return Arrays.stream(genres.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private ComicStatus toComicStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return ComicStatus.DRAFT;
        }
        try {
            return ComicStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ComicStatus.DRAFT;
        }
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
