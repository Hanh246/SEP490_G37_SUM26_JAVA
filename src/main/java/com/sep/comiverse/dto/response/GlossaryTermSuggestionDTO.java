package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlossaryTermSuggestionDTO {

    private UUID id;

    private String source;

    private String target;

    private String note;

    private String matchedText;
}
