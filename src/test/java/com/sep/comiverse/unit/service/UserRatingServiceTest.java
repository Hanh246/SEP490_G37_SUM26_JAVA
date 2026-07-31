package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.UserRatingService;

import com.sep.comiverse.dto.response.ComicRatingResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.UserRatingEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IUserRatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserRatingServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private IUserRatingRepository userRatingRepository;

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private ComicCrudPlugin comicCrudPlugin;

    private UserRatingService userRatingService;

    private final UUID userId = UUID.randomUUID();
    private final UUID comicId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        userRatingService = new UserRatingService(redisTemplate, userRatingRepository, comicRepository, comicCrudPlugin);
    }

    @Test
    void testRateComic_UserNull_ThrowsUnauthorized() {
        CustomException ex = assertThrows(CustomException.class, () ->
                userRatingService.rateComic(comicId, null, 5)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    }

    @Test
    void testRateComic_InvalidScore_ThrowsBadRequest() {
        CustomException ex = assertThrows(CustomException.class, () ->
                userRatingService.rateComic(comicId, userId, 6)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void testRateComic_ComicNotFound_ThrowsNotFound() {
        when(comicRepository.findById(comicId)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () ->
                userRatingService.rateComic(comicId, userId, 4)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void testRateComic_NewRating_Success() {
        ComicEntity comic = ComicEntity.builder().build();
        comic.setId(comicId);

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(userRatingRepository.findByComicIdAndUserId(comicId, userId)).thenReturn(Optional.empty());
        when(userRatingRepository.getAverageRatingByComicId(comicId)).thenReturn(4.5);
        when(userRatingRepository.getRatingCountByComicId(comicId)).thenReturn(2);

        ComicRatingResponse response = userRatingService.rateComic(comicId, userId, 5);

        assertNotNull(response);
        assertEquals(comicId, response.getComicId());
        assertEquals(4.5, response.getRatingAverage());
        assertEquals(2, response.getRatingCount());
        assertEquals(5, response.getUserScore());

        verify(userRatingRepository, times(1)).save(any(UserRatingEntity.class));
        verify(comicRepository, times(1)).save(comic);
        verify(comicCrudPlugin, times(1)).evictComicCache(comicId);
    }

    @Test
    void testRateComic_UpdateExistingRating_Success() {
        ComicEntity comic = ComicEntity.builder().build();
        comic.setId(comicId);

        UserRatingEntity existingRating = UserRatingEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .score(3)
                .build();

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(userRatingRepository.findByComicIdAndUserId(comicId, userId)).thenReturn(Optional.of(existingRating));
        when(userRatingRepository.getAverageRatingByComicId(comicId)).thenReturn(4.0);
        when(userRatingRepository.getRatingCountByComicId(comicId)).thenReturn(1);

        ComicRatingResponse response = userRatingService.rateComic(comicId, userId, 4);

        assertNotNull(response);
        assertEquals(4, existingRating.getScore());
        assertEquals(4.0, response.getRatingAverage());
        assertEquals(1, response.getRatingCount());
        assertEquals(4, response.getUserScore());
    }

    @Test
    void testDeleteRating_Success() {
        ComicEntity comic = ComicEntity.builder().build();
        comic.setId(comicId);

        UserRatingEntity existingRating = UserRatingEntity.builder()
                .userId(userId)
                .comicId(comicId)
                .score(4)
                .build();

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(userRatingRepository.findByComicIdAndUserIdAndDeletedFalse(comicId, userId)).thenReturn(Optional.of(existingRating));
        when(userRatingRepository.getAverageRatingByComicId(comicId)).thenReturn(0.0);
        when(userRatingRepository.getRatingCountByComicId(comicId)).thenReturn(0);

        ComicRatingResponse response = userRatingService.deleteRating(comicId, userId);

        assertNotNull(response);
        assertTrue(existingRating.getDeleted());
        assertEquals(0.0, response.getRatingAverage());
        assertEquals(0, response.getRatingCount());
        assertNull(response.getUserScore());
    }

    @Test
    void testGetComicRating_Success() {
        ComicEntity comic = ComicEntity.builder()
                .ratingAverage(4.8)
                .ratingCount(10)
                .build();
        comic.setId(comicId);

        UserRatingEntity rating = UserRatingEntity.builder()
                .score(5)
                .build();

        when(comicRepository.findById(comicId)).thenReturn(Optional.of(comic));
        when(userRatingRepository.findByComicIdAndUserIdAndDeletedFalse(comicId, userId)).thenReturn(Optional.of(rating));

        ComicRatingResponse response = userRatingService.getComicRating(comicId, userId);

        assertNotNull(response);
        assertEquals(4.8, response.getRatingAverage());
        assertEquals(10, response.getRatingCount());
        assertEquals(5, response.getUserScore());
    }
}
