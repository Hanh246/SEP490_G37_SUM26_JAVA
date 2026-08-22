package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.BroadcastAudienceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BroadcastRequest {

    @NotBlank(message = "Type cannot be blank")
    private String type; // INFO, WARNING, UPDATE, MAINTENANCE

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;

    private BroadcastAudienceType audienceType;

    @Size(max = 10, message = "At most 10 roles can be selected")
    private List<String> targetRoles; // e.g. ["ALL"] or ["ADMIN", "STAFF"]

    @Size(max = 100, message = "At most 100 users can be selected")
    private List<UUID> targetUserIds;

    @Size(max = 20, message = "At most 20 project teams can be selected")
    private List<UUID> targetTeamIds;
}
