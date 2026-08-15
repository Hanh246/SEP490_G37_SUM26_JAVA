package com.sep.comiverse.dto.response;

import com.sep.comiverse.dto.ProjectTeamDTO;
import com.sep.comiverse.entity.TeamTaskEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslatorDashboardDTO {
    private List<ProjectTeamDTO> projects;
    private Map<UUID, TeamStatsDTO> teamStats;
    private List<TeamTaskEntity> activeTasks;
}
