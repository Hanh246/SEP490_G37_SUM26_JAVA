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
public class ChapterPageResponse {
    private UUID id;
    private Integer pageNumber;
    private String imageUrl;
    private String originalFileName;
    private Long fileSizeBytes;
    private Integer width;
    private Integer height;
}
