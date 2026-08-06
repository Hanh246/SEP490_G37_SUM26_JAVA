package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.AppealStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppealResolveRequestDTO {

    @NotNull(message = "Resolution status is required")
    private AppealStatus status;

    private String resolvedReason;
}
