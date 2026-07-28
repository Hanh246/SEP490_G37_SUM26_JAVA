package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDTO {
    private UUID comicId;
    private UUID chapterId;
    private UUID authorId;
    private UUID id;
    private String title;
    private String chapter;
    private String submittedBy;
    private String submittedByEmail; // Submitter's email address
    private String queueType;
    private String timeLabel;
    private Long timestamp;
    private Integer words;
    private String priority;
    private Integer flags;
    private String status;
    private String cover;
    private String content;
    private List<String> genres;
    private String rejectionReason;
    private UUID moderatorId;
    private String moderatorName;
    private String language;
    private Integer minimumAge;
    private String publicationStatus;
    private String summary;
}
