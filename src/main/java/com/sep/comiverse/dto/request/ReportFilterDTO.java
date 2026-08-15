package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFilterDTO {

    @Builder.Default
    @Min(value = 0, message = "Page number must be greater than or equal to 0")
    @Schema(description = "Page number (0-indexed)", example = "0", defaultValue = "0")
    private Integer page = 0;

    @Builder.Default
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    @Schema(description = "Number of items per page", example = "20", defaultValue = "20")
    private Integer size = 20;

    @JsonProperty("status")
    @Schema(description = "Filter by report status", example = "PENDING", allowableValues = {"PENDING", "IN_PROGRESS", "ACCEPTED", "REJECTED"})
    private ReportStatus status;

    @JsonProperty("target_type")
    @JsonAlias({"targetType", "target_type"})
    @Schema(description = "Filter by target type", example = "COMIC", allowableValues = {"COMIC", "CHAPTER", "CHAPTER_TRANSLATIONS"})
    private ReportTargetType targetType;

    @JsonProperty("category_id")
    @JsonAlias({"categoryId", "category_id"})
    @Schema(description = "Filter by report category UUID")
    private UUID categoryId;

    @JsonProperty("assigned_role")
    @JsonAlias({"assignedRole", "assigned_role"})
    @Schema(description = "Filter by category assigned handling role", allowableValues = {"MODERATOR", "PROJECT_LEADER"})
    private ReportAssignedRole assignedRole;

    @JsonProperty("reporter_id")
    @JsonAlias({"reporterId", "reporter_id"})
    @Schema(description = "Filter by reporter UUID")
    private UUID reporterId;

    @JsonProperty("handler_id")
    @JsonAlias({"handlerId", "handler_id"})
    @Schema(description = "Filter by handler/moderator UUID")
    private UUID handlerId;

    @JsonProperty("start_date")
    @JsonAlias({"startDate", "start_date"})
    @Schema(description = "Filter by start date (YYYY-MM-DD)", example = "2026-08-01")
    private String startDate;

    @JsonProperty("end_date")
    @JsonAlias({"endDate", "end_date"})
    @Schema(description = "Filter by end date (YYYY-MM-DD)", example = "2026-08-31")
    private String endDate;

    @Builder.Default
    @JsonProperty("sort_by")
    @JsonAlias({"sortBy", "sort_by"})
    @Schema(description = "Sort field name", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Builder.Default
    @JsonProperty("sort_direction")
    @JsonAlias({"sortDirection", "sort_direction"})
    @Schema(description = "Sort direction", defaultValue = "DESC", allowableValues = {"ASC", "DESC"})
    private String sortDirection = "DESC";
}
