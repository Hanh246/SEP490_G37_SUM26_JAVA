package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

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

    @NotEmpty(message = "Target roles cannot be empty")
    private List<String> targetRoles; // e.g. ["ALL"] or ["ADMIN", "STAFF"]
}
