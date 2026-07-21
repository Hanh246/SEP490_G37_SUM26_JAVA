package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshot {
    private UUID userId;
    private String userName;
    private String avatarURL;
}
