package com.sep.comiverse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportCategoryResponse {

    @JsonProperty("id")
    @Schema(description = "Category unique identifier")
    private UUID id;

    @JsonProperty("name")
    @Schema(description = "Category name", example = "Image & Page Issue")
    private String name;

    @JsonProperty("description")
    @Schema(description = "Category description", example = "Issues related to chapter images")
    private String description;

    @JsonProperty("assigned_role")
    @Schema(description = "Role assigned to handle this category", example = "MODERATOR")
    private ReportAssignedRole assignedRole;

    @Builder.Default
    @JsonProperty("target_types")
    @Schema(description = "List of supported report target types", example = "[\"CHAPTER\"]")
    private List<ReportTargetType> targetTypes = new ArrayList<>();

    @JsonProperty("is_active")
    @Schema(description = "Whether the category is currently active", example = "true")
    private Boolean isActive;

    @JsonProperty("created_by")
    @Schema(description = "UUID of the user who created this category")
    private UUID createdBy;

    @JsonProperty("created_by_name")
    @Schema(description = "Display name of the user who created this category")
    private String createdByName;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the category was created")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the category was last updated")
    private Instant updatedAt;
}
