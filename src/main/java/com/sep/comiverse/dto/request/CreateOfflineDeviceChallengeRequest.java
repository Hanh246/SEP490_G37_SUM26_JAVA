package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOfflineDeviceChallengeRequest {

    @NotBlank
    @Size(min = 16, max = 128)
    private String deviceId;

    @Size(max = 120)
    private String deviceName;

    @NotBlank
    @Size(max = 8192)
    private String devicePublicKey;
}
