package com.sep.comiverse.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ReportAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessReportRequest {

    @NotNull(message = "Processing action is required (ACCEPT or REJECT)")
    @JsonProperty("action")
    @Schema(description = "Processing action: ACCEPT or REJECT", example = "ACCEPT", allowableValues = {"ACCEPT", "REJECT"})
    private ReportAction action;

    @JsonProperty("resolution_note")
    @JsonAlias({"resolutionNote", "resolution_note", "note"})
    @Schema(description = "Moderator / Leader resolution note (required when rejecting any report, and when accepting a translation report)", example = "Confirmed issue and replaced broken chapter images.")
    private String resolutionNote;
}
