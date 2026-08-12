package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ReopenAuthorLicenseRequest {

    @Min(value = 1, message = "Deadline must be at least 1 day")
    @Max(value = 30, message = "Deadline must not exceed 30 days")
    private Integer deadlineDays;
}
