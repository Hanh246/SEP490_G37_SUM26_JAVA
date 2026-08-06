package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.AppealStatus;
import com.sep.comiverse.entity.enums.AppealTargetType;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AppealTicketResponseDTO {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private UUID targetId;
    private String targetName; // Could be comic title or chapter number
    private AppealTargetType targetType;
    private String appealReason;
    private String evidenceUrls;
    private AppealStatus status;
    private UUID resolvedByModId;
    private String resolvedByModName;
    private String resolvedReason;
    private String previousStateSnapshot;
    private Instant createdAt;
    private Instant updatedAt;
}
