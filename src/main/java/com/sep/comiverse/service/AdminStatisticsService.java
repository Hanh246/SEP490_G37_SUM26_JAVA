package com.sep.comiverse.service;

import com.sep.comiverse.dto.GenreDTO;
import com.sep.comiverse.dto.response.AdminStatisticsResponse;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IGenreRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.repository.IUserLikeRepository;
import com.sep.comiverse.repository.IUserSaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private static final List<String> PLATFORM_ROLES = List.of(
            "READER",
            "AUTHOR",
            "TRANSLATOR",
            "PROJECT_LEADER",
            "MODERATOR",
            "ADMIN"
    );

    private final IUserRepository userRepository;
    private final IComicRepository comicRepository;
    private final IGenreRepository genreRepository;
    private final ISubmissionRepository submissionRepository;
    private final IUserLikeRepository userLikeRepository;
    private final IUserSaveRepository userSaveRepository;

    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics() {
        Map<String, Long> roleCounts = new LinkedHashMap<>();
        PLATFORM_ROLES.forEach(role -> roleCounts.put(role, 0L));

        userRepository.countUsersByRole().forEach(row -> {
            String role = row[0] == null
                    ? "UNASSIGNED"
                    : row[0].toString().trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
            roleCounts.merge(role, ((Number) row[1]).longValue(), Long::sum);
        });

        Map<String, Long> comicStatusCounts = new LinkedHashMap<>();
        for (com.sep.comiverse.entity.enums.ComicPublicationStatus status : com.sep.comiverse.entity.enums.ComicPublicationStatus.values()) {
            comicStatusCounts.put(status.name(), 0L);
        }
        comicRepository.countComicsByPublicationStatus().forEach(row -> {
            if (row[0] != null) {
                comicStatusCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        });

        List<GenreDTO> genres = genreRepository
                .findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.ASC, "name")))
                .getContent()
                .stream()
                .map(genre -> new GenreDTO(genre.getId(), genre.getName(), genre.getSlug()))
                .toList();

        Instant startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant fifteenMinsAgo = Instant.now().minus(15, java.time.temporal.ChronoUnit.MINUTES);

        return AdminStatisticsResponse.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByStatusIgnoreCaseAndDeletedFalse("ACTIVE"))
                .bannedUsers(userRepository.countByStatusIgnoreCaseAndDeletedFalse("INACTIVE"))
                .totalPublishedComics(comicRepository.countByModerationStatusAndDeletedFalse(ComicModerationStatus.PUBLISHED))
                .totalGenres(genreRepository.count())
                .pendingSubmissions(submissionRepository.countByStatusIgnoreCaseAndDeletedFalse("pending"))
                .newUsersToday(userRepository.countByCreatedAtGreaterThanEqualAndDeletedFalse(startOfToday))
                .newComicsToday(comicRepository.countByUpdatedAtGreaterThanEqualAndModerationStatusAndDeletedFalse(startOfToday, ComicModerationStatus.PUBLISHED))
                .activeUsersToday(userRepository.countByLastSeenAtGreaterThanEqualAndDeletedFalse(startOfToday))
                .onlineUsersNow(userRepository.countByLastSeenAtGreaterThanEqualAndDeletedFalse(fifteenMinsAgo))
                .newLikesToday(userLikeRepository.countByCreatedAtGreaterThanEqualAndDeletedFalse(startOfToday))
                .newBookmarksToday(userSaveRepository.countByCreatedAtGreaterThanEqualAndDeletedFalse(startOfToday))
                .roleCounts(roleCounts)
                .comicStatusCounts(comicStatusCounts)
                .genres(genres)
                .generatedAt(Instant.now())
                .build();
    }
}
