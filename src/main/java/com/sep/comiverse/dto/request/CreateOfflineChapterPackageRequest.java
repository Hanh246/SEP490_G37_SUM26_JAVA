package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOfflineChapterPackageRequest {

    @NotNull
    private UUID deviceKeyId;

    /**
     * New mobile clients request CVPACK v2 so approved translation overlays
     * are available offline. The default remains false for v1 clients.
     */
    private boolean includeTranslations;
}
