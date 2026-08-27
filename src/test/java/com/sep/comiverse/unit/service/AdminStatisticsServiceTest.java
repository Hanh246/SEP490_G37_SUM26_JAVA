package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.AdminStatisticsResponse;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.GenreEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.repository.IUserRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IComicRepository comicRepository;

    @Mock
    private IGenreRepository genreRepository;

    @Mock
    private ISubmissionRepository submissionRepository;

    // NEW
    @Mock
    private IUserLikeRepository userLikeRepository;

    // NEW
    @Mock
    private IUserSaveRepository userSaveRepository;

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

        /*
         * Service mới tính totalPublishedComics từ
         * findAllByDeletedFalseWithGenres(), không còn dùng
         * countByModerationStatusAndDeletedFalse(PUBLISHED).
         */
        ComicEntity comic = new ComicEntity();
        comic.setId(UUID.randomUUID());
        comic.setTitle("Test Comic");
        comic.setModerationStatus(ComicModerationStatus.PUBLISHED);
        comic.setPublicationStatus(ComicPublicationStatus.ONGOING);

        when(userRepository.count()).thenReturn(130L);

        when(userRepository.countByStatusIgnoreCaseAndDeletedFalse("ACTIVE"))
                .thenReturn(120L);

        when(userRepository.countByStatusIgnoreCaseAndDeletedFalse("INACTIVE"))
                .thenReturn(4L);

        when(userRepository.countUsersByRole()).thenReturn(List.of(
                new Object[]{"READER", 100L},
                new Object[]{"PROJECT_LEADER", 3L},
                new Object[]{"ADMIN", 2L}
        ));

        when(comicRepository.findAllByDeletedFalseWithGenres())
                .thenReturn(List.of(comic));

        when(genreRepository.count()).thenReturn(12L);

        when(genreRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(action)));

        when(submissionRepository
                .countByStatusIgnoreCaseAndDeletedFalse("pending"))
                .thenReturn(7L);

        /*
         * Các statistic mới.
         * Có thể để Mockito trả 0 mặc định,
         * nhưng mock rõ sẽ làm test dễ hiểu hơn.
         */
        when(userRepository
                .countByCreatedAtGreaterThanEqualAndDeletedFalse(any()))
                .thenReturn(0L);

        when(comicRepository
                .countByUpdatedAtGreaterThanEqualAndModerationStatusAndDeletedFalse(
                        any(),
                        any(ComicModerationStatus.class)
                ))
                .thenReturn(0L);

        when(userRepository
                .countByLastSeenAtGreaterThanEqualAndDeletedFalse(any()))
                .thenReturn(0L);

        when(userLikeRepository
                .countByCreatedAtGreaterThanEqualAndDeletedFalse(any()))
                .thenReturn(0L);

        when(userSaveRepository
                .countByCreatedAtGreaterThanEqualAndDeletedFalse(any()))
                .thenReturn(0L);

        AdminStatisticsResponse result = service.getStatistics();

        assertEquals(130L, result.getTotalUsers());
        assertEquals(120L, result.getActiveUsers());
        assertEquals(4L, result.getBannedUsers());

        // Service mới có 1 comic mock -> expected = 1
        assertEquals(1L, result.getTotalPublishedComics());

        assertEquals(12L, result.getTotalGenres());
        assertEquals(7L, result.getPendingSubmissions());

        assertEquals(100L, result.getRoleCounts().get("READER"));
        assertEquals(3L, result.getRoleCounts().get("PROJECT_LEADER"));

        // PLATFORM_ROLES khởi tạo TRANSLATOR = 0
        assertEquals(0L, result.getRoleCounts().get("TRANSLATOR"));

        assertEquals("Action", result.getGenres().getFirst().getName());
    }
}