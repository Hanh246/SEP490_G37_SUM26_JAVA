package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.PageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPageDTO {
    private java.util.UUID pageId;
    private Integer pageNumber;
    private String imageUrl;
    private PageStatus status;

}
