package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotNull(message = "Report target type (target_type) is required")
    @JsonProperty("target_type")
    @JsonAlias({"targetType", "target_type"})
    @Schema(description = "Type of target entity being reported", example = "COMIC", allowableValues = {"COMIC", "CHAPTER", "CHAPTER_TRANSLATIONS"})
    private ReportTargetType targetType;

    @NotNull(message = "Report target ID (target_id) is required")
    @JsonProperty("target_id")
    @JsonAlias({"targetId", "target_id"})
    @Schema(description = "UUID of the target Comic, Chapter, or Chapter Translation")
    private UUID targetId;

    @NotNull(message = "Report category ID (category_id) is required")
    @JsonProperty("category_id")
    @JsonAlias({"categoryId", "category_id"})
    @Schema(description = "UUID of the report category (report_categories.id)")
    private UUID categoryId;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @JsonProperty("description_text")
    @JsonAlias({"descriptionText", "description_text", "description"})
    @Schema(description = "Detailed user description or reason for this report", example = "The pages in chapter 5 are blurred and broken.")
    private String descriptionText;

    @JsonProperty("language_code")
    @JsonAlias({"languageCode", "language_code", "lang"})
    @Schema(description = "Optional reading language when reporting a chapter translation by chapter ID", example = "vi")
    private String languageCode;
}
