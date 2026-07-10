package com.sep.comiverse.service;

import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.repository.IUserSaveRepository;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.repository.IAuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecommendationServiceTest {

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IUserLikeRepository userLikeRepository;

    @Mock
    private IUserSaveRepository userSaveRepository;

    @Mock
    private IReadingHistoryRepository readingHistoryRepository;

    @Mock
    private IAuthorRepository authorRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private RestTemplate restTemplate;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                comicRepository,
                userRepository,
                userLikeRepository,
                userSaveRepository,
                readingHistoryRepository,
                authorRepository,
                redisTemplate
        );
        ReflectionTestUtils.setField(recommendationService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(recommendationService, "apiKey", "dummy-api-key");
    }

    @Test
    void testGenerateComicVector_Success() {
        // Arrange
        String summary = "Action comic";
        Map<String, Object> mockResponse = new HashMap<>();
        Map<String, Object> mockEmbedding = new HashMap<>();
        List<Number> mockValues = new ArrayList<>();
        for (int i = 0; i < 768; i++) {
            mockValues.add(0.1f * i);
        }
        mockEmbedding.put("values", mockValues);
        mockResponse.put("embedding", mockEmbedding);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        float[] result = recommendationService.generateComicVector(summary);

        // Assert
        assertNotNull(result);
        assertEquals(768, result.length);
        assertEquals(0.0f, result[0]);
        assertEquals(0.1f, result[1]);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void testGenerateComicVector_EmptyText() {
        // Act
        float[] result = recommendationService.generateComicVector("   ");

        // Assert
        assertNull(result);
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void testGenerateUserPreferenceVector_NoInteractions() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userLikeRepository.findLikedComicIdsByUserId(userId)).thenReturn(Collections.emptyList());
        when(userSaveRepository.findSavedComicIdsByUserId(userId)).thenReturn(Collections.emptyList());
        when(readingHistoryRepository.findReadComicIdsByUserId(userId)).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Collections.emptySet());

        // Act
        float[] result = recommendationService.generateUserPreferenceVector(userId);

        // Assert
        assertNull(result);
        assertNull(user.getUserVector());
        verify(userRepository).save(user);
    }

    @Test
    void testGenerateUserPreferenceVector_Calculation() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        UUID comicId1 = UUID.randomUUID();
        UUID comicId2 = UUID.randomUUID();

        float[] vec1 = new float[768];
        float[] vec2 = new float[768];
        Arrays.fill(vec1, 1.0f);
        Arrays.fill(vec2, 3.0f);

        ComicEntity comic1 = new ComicEntity();
        comic1.setId(comicId1);
        comic1.setSummaryVector(vec1);

        ComicEntity comic2 = new ComicEntity();
        comic2.setId(comicId2);
        comic2.setSummaryVector(vec2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userLikeRepository.findLikedComicIdsByUserId(userId)).thenReturn(List.of(comicId1));
        when(userSaveRepository.findSavedComicIdsByUserId(userId)).thenReturn(List.of(comicId2));
        when(readingHistoryRepository.findReadComicIdsByUserId(userId)).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Collections.emptySet());

        when(comicRepository.findById(comicId1)).thenReturn(Optional.of(comic1));
        when(comicRepository.findById(comicId2)).thenReturn(Optional.of(comic2));

        // Act
        float[] result = recommendationService.generateUserPreferenceVector(userId);

        // Assert
        assertNotNull(result);
        assertEquals(768, result.length);
        assertEquals(2.0f, result[0]);
        assertEquals(2.0f, result[767]);
        assertArrayEquals(result, user.getUserVector());
        verify(userRepository).save(user);
    }

    @Test
    void testGetRecommendationsForUser_Fallback() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUserVector(null);

        ComicEntity popularComic = new ComicEntity();
        popularComic.setTitle("Popular Comic");

        Page<ComicEntity> page = new PageImpl<>(List.of(popularComic));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(comicRepository.findByDeletedFalseAndModerationStatusOrderByViewCountDesc(
                eq(ComicModerationStatus.PUBLISHED), any(PageRequest.class)
        )).thenReturn(page);

        // Act
        List<ComicEntity> results = recommendationService.getRecommendationsForUser(userId, 5);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Popular Comic", results.get(0).getTitle());
        verify(comicRepository, never()).findRecommendedComicsForUser(any(), anyInt());
    }

    @Test
    void testGetRecommendationsForUser_Recommendation() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        float[] userVec = new float[768];
        user.setUserVector(userVec);

        ComicEntity recommendedComic = new ComicEntity();
        recommendedComic.setTitle("Recommended Comic");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(comicRepository.findRecommendedComicsForUser(userId, 5)).thenReturn(List.of(recommendedComic));

        // Act
        List<ComicEntity> results = recommendationService.getRecommendationsForUser(userId, 5);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Recommended Comic", results.get(0).getTitle());
        verify(comicRepository).findRecommendedComicsForUser(userId, 5);
    }
}
