package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannedKeywordDTO {
    private UUID id;
    private String word;
    private String category;
    private String severity;
}
