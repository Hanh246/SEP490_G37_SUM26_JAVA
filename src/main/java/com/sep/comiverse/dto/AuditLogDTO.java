package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private String actionType;
    private String description;
    private Instant createdAt;
}
