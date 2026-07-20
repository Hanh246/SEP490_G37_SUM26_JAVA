package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.PageStatus;
import lombok.*;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPageDTO {
    private java.util.UUID pageId;
    private Integer pageNumber;
    private String imageUrl;
    private PageStatus status;
    private String bubbles;
    private String reviewBaselineBubbles;

}
