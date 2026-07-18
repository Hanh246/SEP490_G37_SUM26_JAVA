package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTeamDTO {
    private UUID id;
    private String title;
    private String comicName;
    private String status;
    private Integer membersCount;
    private Integer chaptersCount;
    private Integer progress;
    private String leaderName;
    private UUID leaderId;
    private String leaderInitials;
    private String deadline;
    private String sourceLang;
    private String targetLang;
    private String priority;
    private String cover;
    private String description;
    private String notes;
    private String comicTitle;
    private Boolean assignedToMe;
    private Boolean isRecruiting;
    private Integer maxMembers;
    private List<ChapterDTO> chaptersList;
}
