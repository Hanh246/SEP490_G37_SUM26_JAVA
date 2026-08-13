package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDeviceResponse {
    private UUID id;
    private String deviceName;
    private String platform;
    private Instant verifiedAt;
    private Instant lastSeenAt;
    private boolean current;
}
