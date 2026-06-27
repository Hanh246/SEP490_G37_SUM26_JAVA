package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatFlagDTO {
    private UUID id;
    private String user;
    private String message;
    private String reason;
    private String status;
}
