package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.AppealTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AppealTicketRequestDTO {
    
    @NotNull(message = "Target ID is required")
    private UUID targetId;
    
    @NotNull(message = "Target Type is required")
    private AppealTargetType targetType;
    
    @NotBlank(message = "Appeal reason cannot be blank")
    private String appealReason;
    
    private String evidenceUrls;
}
