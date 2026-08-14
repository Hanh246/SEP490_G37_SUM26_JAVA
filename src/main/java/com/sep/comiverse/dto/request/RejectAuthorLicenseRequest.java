package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectAuthorLicenseRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 2000, message = "Rejection reason must not exceed 2000 characters")
    private String reason;

    /** New re-upload deadline. Defaults to 7 days when omitted. */
    @Min(value = 1, message = "Deadline must be at least 1 day")
    @Max(value = 30, message = "Deadline must not exceed 30 days")
    private Integer deadlineDays;
}
