package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.AdminStatisticsResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.repository.IUserSaveRepository;
import com.sep.comiverse.service.AdminStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceTest {

    @Mock private IUserRepository userRepository;
    @Mock private IComicRepository comicRepository;
    @Mock private IGenreRepository genreRepository;
    @Mock private ISubmissionRepository submissionRepository;
    @Mock private IUserLikeRepository userLikeRepository;
    @Mock private IUserSaveRepository userSaveRepository;

    private AdminStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsService(
                userRepository,
                comicRepository,
                genreRepository,
                submissionRepository,
                userLikeRepository,
                userSaveRepository
        );
    }

    @Test
    void returnsDatabaseCountsAndKeepsProjectLeaderSeparateFromReader() {
        GenreEntity action = new GenreEntity();
        action.setId(UUID.randomUUID());
        action.setName("Action");
        action.setSlug("action");

        when(userRepository.count()).thenReturn(130L);
        when(userRepository.countByStatusIgnoreCaseAndDeletedFalse("ACTIVE")).thenReturn(120L);
        when(userRepository.countByStatusIgnoreCaseAndDeletedFalse("INACTIVE")).thenReturn(4L);
        when(userRepository.countUsersByRole()).thenReturn(List.of(
                new Object[]{"READER", 100L},
                new Object[]{"PROJECT_LEADER", 3L},
                new Object[]{"ADMIN", 2L}
        ));
        when(comicRepository.findAllByDeletedFalseWithGenres()).thenReturn(
                IntStream.range(0, 42)
                        .mapToObj(index -> {
                            ComicEntity comic = new ComicEntity();
                            comic.setTitle("Published Comic " + index);
                            comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
                            return comic;
                        })
                        .toList()
        );
        when(genreRepository.count()).thenReturn(12L);
        when(genreRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(action)));
        when(submissionRepository.countByStatusIgnoreCaseAndDeletedFalse("pending")).thenReturn(7L);

        AdminStatisticsResponse result = service.getStatistics();

        assertEquals(130L, result.getTotalUsers());
        assertEquals(120L, result.getActiveUsers());
        assertEquals(4L, result.getBannedUsers());
        assertEquals(42L, result.getTotalPublishedComics());
        assertEquals(12L, result.getTotalGenres());
        assertEquals(7L, result.getPendingSubmissions());
        assertEquals(100L, result.getRoleCounts().get("READER"));
        assertEquals(3L, result.getRoleCounts().get("PROJECT_LEADER"));
        assertEquals(0L, result.getRoleCounts().get("TRANSLATOR"));
        assertEquals("Action", result.getGenres().getFirst().getName());
    }
}
