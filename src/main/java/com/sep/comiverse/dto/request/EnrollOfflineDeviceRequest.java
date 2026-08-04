package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnrollOfflineDeviceRequest {

    @NotBlank
    @Size(max = 36)
    private String challengeId;

    @NotBlank
    @Size(max = 8192)
    private String signature;
}
