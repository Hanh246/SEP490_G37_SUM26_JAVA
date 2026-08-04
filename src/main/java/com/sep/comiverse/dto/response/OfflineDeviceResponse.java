package com.sep.comiverse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OfflineDeviceResponse {
    private UUID deviceKeyId;
    private String deviceName;
    private String publicKeySha256;
    private Instant enrolledAt;
    private Instant lastSeenAt;
    private Instant serverTime;
    private boolean revoked;
}
