package com.sep.comiverse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    @JsonProperty("id")
    @Schema(description = "Report unique identifier")
    private UUID id;

    @JsonProperty("reporter_id")
    @Schema(description = "UUID of the reporting user")
    private UUID reporterId;

    @JsonProperty("reporter_name")
    @Schema(description = "Display name of the reporting user")
    private String reporterName;

    @JsonProperty("reporter_email")
    @Schema(description = "Email of the reporting user")
    private String reporterEmail;

    @JsonProperty("reporter_avatar_url")
    @Schema(description = "Avatar URL of the reporting user")
    private String reporterAvatarUrl;

    @JsonProperty("target_type")
    @Schema(description = "Type of target entity", example = "COMIC")
    private ReportTargetType targetType;

    @JsonProperty("target_id")
    @Schema(description = "UUID of the reported entity")
    private UUID targetId;

    @JsonProperty("target_title")
    @Schema(description = "Human-readable title or label of the reported entity", example = "One Piece - Chapter 1000")
    private String targetTitle;

    @JsonProperty("target_url")
    @Schema(description = "Direct deep link or reference URL to the target")
    private String targetUrl;

    @JsonProperty("category_id")
    @Schema(description = "UUID of the report category")
    private UUID categoryId;

    @JsonProperty("category_name")
    @Schema(description = "Name of the report category", example = "Image & Page Issue")
    private String categoryName;

    @JsonProperty("category_assigned_role")
    @Schema(description = "Role assigned to handle this category", example = "MODERATOR")
    private ReportAssignedRole categoryAssignedRole;

    @JsonProperty("description_text")
    @Schema(description = "Detailed report description from user")
    private String descriptionText;

    @JsonProperty("status")
    @Schema(description = "Current report status", example = "PENDING")
    private ReportStatus status;

    @JsonProperty("handler_id")
    @Schema(description = "UUID of the moderator or leader handling this report")
    private UUID handlerId;

    @JsonProperty("handler_name")
    @Schema(description = "Display name of the handling moderator or leader")
    private String handlerName;

    @JsonProperty("handler_email")
    @Schema(description = "Email of the handling moderator or leader")
    private String handlerEmail;

    @JsonProperty("resolution_note")
    @Schema(description = "Resolution note from moderator or leader")
    private String resolutionNote;

    @JsonProperty("resolved_at")
    @Schema(description = "Timestamp when the report was resolved")
    private Instant resolvedAt;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the report was created")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the report was last updated")
    private Instant updatedAt;
}
