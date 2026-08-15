package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStatsDTO {
    private long totalTasks;
    private long backlog;
    private long inProgress;
    private long review;
    private long done;
    private long totalChapters;
}
