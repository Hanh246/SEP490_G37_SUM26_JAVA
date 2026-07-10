package com.sep.comiverse.service;

import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import com.sep.comiverse.dto.pagination.CursorResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final IComicRepository comicRepository;
    private final IUserRepository userRepository;
    private final IUserLikeRepository userLikeRepository;
    private final IUserSaveRepository userSaveRepository;
    private final IReadingHistoryRepository readingHistoryRepository;
    private final IAuthorRepository authorRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    public float[] generateComicVector(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            log.warn("Summary text is empty; returning zero vector or null.");
            return null;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + apiKey;

        // Construct request payload
        Map<String, Object> part = Map.of("text", summary);
        Map<String, Object> contents = Map.of("parts", List.of(part));
        Map<String, Object> payload = Map.of(
                "model", "models/gemini-embedding-001",
                "content", contents,
                "outputDimensionality", 768
        );

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, payload, Map.class);
            Map<String, Object> body = responseEntity.getBody();
            if (body != null && body.containsKey("embedding")) {
                Map<String, Object> embedding = (Map<String, Object>) body.get("embedding");
                if (embedding != null && embedding.containsKey("values")) {
                    List<Number> values = (List<Number>) embedding.get("values");
                    if (values != null && values.size() == 768) {
                        float[] vector = new float[768];
                        for (int i = 0; i < 768; i++) {
                            vector[i] = values.get(i).floatValue();
                        }
                        return vector;
                    }
                }
            }
            log.error("Gemini API response did not contain embedding.values or was not of size 768");
        } catch (Exception e) {
            log.error("Failed to generate embedding from Gemini API: " + e.getMessage(), e);
        }
        return null;
    }

    @Transactional
    public void updateComicVector(UUID comicId) {
        ComicEntity comic = comicRepository.findById(comicId)
                .orElseThrow(() -> new IllegalArgumentException("Comic not found for id: " + comicId));
        String text = buildSummaryText(comic);
        float[] vector = generateComicVector(text);
        if (vector != null) {
            comic.setSummaryVector(vector);
            comicRepository.save(comic);
            log.info("Successfully updated summary_vector for comic: {}", comic.getTitle());
        }
    }

    public String buildSummaryText(ComicEntity comic) {
        StringBuilder sb = new StringBuilder();
        sb.append("Comic Name: ").append(comic.getTitle()).append(". ");
        if (comic.getSummary() != null && !comic.getSummary().trim().isEmpty()) {
            sb.append("Summary: ").append(comic.getSummary().trim()).append(". ");
        }
        if (comic.getGenres() != null && !comic.getGenres().isEmpty()) {
            String genresStr = comic.getGenres().stream()
                    .map(GenreEntity::getName)
                    .collect(Collectors.joining(", "));
            sb.append("Genres: ").append(genresStr).append(". ");
        }
        if (comic.getAuthorId() != null) {
            authorRepository.findByUserIdAndDeletedFalse(comic.getAuthorId()).ifPresent(author -> {
                sb.append("Author: ").append(author.getDisplayName()).append(". ");
            });
        }
        return sb.toString().trim();
    }

    @Transactional
    public float[] generateUserPreferenceVector(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found for id: " + userId));

        Set<UUID> comicIds = getInteractedComicIds(userId);
        if (comicIds.isEmpty()) {
            log.info("User {} has no interactions. user_vector set to null.", userId);
            user.setUserVector(null);
            user.setVectorUpdatedAt(java.time.Instant.now());
            userRepository.save(user);
            return null;
        }

        List<float[]> vectors = new ArrayList<>();
        for (UUID comicId : comicIds) {
            comicRepository.findById(comicId).ifPresent(comic -> {
                if (comic.getSummaryVector() != null && comic.getSummaryVector().length == 768) {
                    vectors.add(comic.getSummaryVector());
                }
            });
        }

        if (vectors.isEmpty()) {
            log.info("Interacted comics for user {} do not have vectors yet. user_vector remains null/unchanged.", userId);
            return null;
        }

        float[] avgVector = new float[768];
        for (float[] v : vectors) {
            for (int i = 0; i < 768; i++) {
                avgVector[i] += v[i];
            }
        }
        for (int i = 0; i < 768; i++) {
            avgVector[i] /= vectors.size();
        }

        user.setUserVector(avgVector);
        user.setVectorUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
        log.info("Successfully updated user_vector for user: {}", userId);
        return avgVector;
    }

    public Set<UUID> getInteractedComicIds(UUID userId) {
        Set<UUID> comicIds = new HashSet<>();

        // 1. Get likes from DB
        comicIds.addAll(userLikeRepository.findLikedComicIdsByUserId(userId));

        // 2. Get saves from DB
        comicIds.addAll(userSaveRepository.findSavedComicIdsByUserId(userId));

        // 3. Get read history from DB
        comicIds.addAll(readingHistoryRepository.findReadComicIdsByUserId(userId));

        // 4. Combine with Redis queue
        try {
            Set<Object> queued = redisTemplate.opsForSet().members("reading:history:sync:queue");
            if (queued != null) {
                for (Object obj : queued) {
                    String entry = (String) obj;
                    String[] parts = entry.split(":");
                    if (parts.length == 3) {
                        UUID entryUserId = UUID.fromString(parts[2]);
                        if (entryUserId.equals(userId)) {
                            comicIds.add(UUID.fromString(parts[0]));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query reading history from Redis queue for user " + userId, e);
        }

        return comicIds;
    }

    @Transactional(readOnly = true)
    public List<ComicEntity> getSimilarComics(UUID currentComicId, int limit) {
        return comicRepository.findSimilarComics(currentComicId, limit);
    }

    @Transactional(readOnly = true)
    public List<ComicEntity> getRecommendationsForUser(UUID userId, int limit) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getUserVector() == null) {
            return comicRepository.findByDeletedFalseAndModerationStatusOrderByViewCountDesc(
                    ComicModerationStatus.PUBLISHED, PageRequest.of(0, limit)
            ).getContent();
        }
        return comicRepository.findRecommendedComicsForUser(userId, limit);
    }

    @Transactional(readOnly = true)
    public CursorResponseDTO<UUID> getRecommendedComicIdsCursor(UUID userId, String cursor, UUID referenceId, int size) {
        int limit = size + 1;
        List<UUID> ids;
        boolean isVectorSearch = false;
        UserEntity user = null;

        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getUserVector() != null) {
                isVectorSearch = true;
            }
        }

        if (isVectorSearch) {
            Double cursorDistance = null;
            if (cursor != null && !cursor.trim().isEmpty()) {
                try {
                    cursorDistance = Double.parseDouble(cursor.trim());
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse cursor distance: {}", cursor);
                }
            }
            ids = comicRepository.findRecommendedComicIdsForUserCursor(userId, cursorDistance, referenceId, limit);
        } else {
            Long cursorVal = null;
            if (cursor != null && !cursor.trim().isEmpty()) {
                try {
                    cursorVal = Long.parseLong(cursor.trim());
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse cursor view count: {}", cursor);
                }
            }
            ids = comicRepository.findPopularComicIdsCursor(cursorVal, referenceId, limit);
        }

        boolean hasMore = ids.size() > size;
        List<UUID> resultIds = hasMore ? ids.subList(0, size) : ids;

        String nextCursor = null;
        UUID nextReferenceId = null;

        if (!resultIds.isEmpty() && hasMore) {
            UUID lastId = resultIds.get(resultIds.size() - 1);
            nextReferenceId = lastId;
            ComicEntity lastComic = comicRepository.findById(lastId).orElse(null);
            if (lastComic != null) {
                if (isVectorSearch && user != null) {
                    double dist = calculateCosineDistance(user.getUserVector(), lastComic.getSummaryVector());
                    nextCursor = String.valueOf(dist);
                } else {
                    nextCursor = String.valueOf(lastComic.getViewCount());
                }
            }
        }

        return new CursorResponseDTO<>(resultIds, nextCursor, nextReferenceId, hasMore);
    }

    private double calculateCosineDistance(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 1.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }
        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        return 1.0 - similarity;
    }
}
