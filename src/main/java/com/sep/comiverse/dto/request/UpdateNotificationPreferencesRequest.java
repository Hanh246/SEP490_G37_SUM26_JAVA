package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateNotificationPreferencesRequest {
    @NotNull(message = "Preferences are required")
    private Map<String, Boolean> preferences;
}
