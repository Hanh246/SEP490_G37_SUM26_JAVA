package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.PageStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPageDTO {
    private UUID pageId;
    private Integer pageNumber;
    private String imageUrl;
    private PageStatus status;
    private String bubbles;
    private String reviewBaselineBubbles;
    private UUID assignedTranslatorId;
    private BigDecimal responsibilityFactor;
    private Instant completedAt;

}
