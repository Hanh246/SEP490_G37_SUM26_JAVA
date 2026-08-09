package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportCategoryRequest {

    @Size(max = 150, message = "Category name must not exceed 150 characters")
    @JsonProperty("name")
    @Schema(description = "Updated name of the report category", example = "Image & Page Quality Issue")
    private String name;

    @JsonProperty("description")
    @Schema(description = "Updated description of the report category")
    private String description;

    @JsonProperty("assigned_role")
    @JsonAlias({"assignedRole", "assigned_role"})
    @Schema(description = "Role responsible for handling reports under this category", example = "MODERATOR")
    private ReportAssignedRole assignedRole;

    @JsonProperty("target_types")
    @JsonAlias({"targetTypes", "target_types"})
    @Schema(description = "Updated list of supported target types (e.g. COMIC, CHAPTER, CHAPTER_TRANSLATIONS)", example = "[\"CHAPTER\"]")
    private List<ReportTargetType> targetTypes;

    @JsonProperty("is_active")
    @JsonAlias({"isActive", "is_active"})
    @Schema(description = "Active status of the report category")
    private Boolean isActive;
}
