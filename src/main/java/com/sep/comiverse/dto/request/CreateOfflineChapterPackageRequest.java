package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOfflineChapterPackageRequest {

    @NotNull
    private UUID deviceKeyId;
}
