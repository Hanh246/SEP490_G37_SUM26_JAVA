package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private String targetRoles;
    private long recipientCount;
    private Date sentAt;
}
