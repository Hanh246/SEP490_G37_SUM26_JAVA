package com.sep.comiverse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class OfflineDeviceChallengeResponse {
    private String challengeId;
    private String challenge;
    private String signatureAlgorithm;
    private Instant expiresAt;
    private Instant serverTime;
}
