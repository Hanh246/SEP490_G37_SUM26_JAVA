package com.sep.comiverse.dto.response;

import com.sep.comiverse.dto.GenreDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatisticsResponse {
    private long totalUsers;
    private long activeUsers;
    private long bannedUsers;
    private long totalPublishedComics;
    private long totalGenres;
    private long pendingSubmissions;
    private long newUsersToday;
    private long newComicsToday;
    private long activeUsersToday;
    private long onlineUsersNow;
    private Map<String, Long> roleCounts;
    private List<GenreDTO> genres;
    private Instant generatedAt;
}
