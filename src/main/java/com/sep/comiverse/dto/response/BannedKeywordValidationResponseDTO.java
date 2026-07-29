package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannedKeywordValidationResponseDTO {
    private boolean isBanned;
    private String matchedWord;
    private String category;
    private String severity;
    private String reason;
}
