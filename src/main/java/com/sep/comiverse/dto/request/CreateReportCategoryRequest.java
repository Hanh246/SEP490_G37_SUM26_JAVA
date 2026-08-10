package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportCategoryRequest {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 150, message = "Category name must not exceed 150 characters")
    @JsonProperty("name")
    @Schema(description = "Name of the report category", example = "Image & Page Issue")
    private String name;

    @JsonProperty("description")
    @Schema(description = "Detailed description of the report category", example = "Issues related to broken or blurry chapter images")
    private String description;

    @NotNull(message = "Assigned role (assigned_role) must not be null")
    @JsonProperty("assigned_role")
    @JsonAlias({"assignedRole", "assigned_role"})
    @Schema(description = "Role responsible for handling reports under this category", example = "MODERATOR", allowableValues = {"MODERATOR", "PROJECT_LEADER"})
    private ReportAssignedRole assignedRole;

    @Builder.Default
    @JsonProperty("target_types")
    @JsonAlias({"targetTypes", "target_types"})
    @Schema(description = "Supported target types for this category (e.g. COMIC, CHAPTER, CHAPTER_TRANSLATIONS)", example = "[\"CHAPTER\"]")
    private List<ReportTargetType> targetTypes = new ArrayList<>();

    @Builder.Default
    @JsonProperty("is_active")
    @JsonAlias({"isActive", "is_active"})
    @Schema(description = "Whether the report category is active", example = "true")
    private Boolean isActive = true;
}
