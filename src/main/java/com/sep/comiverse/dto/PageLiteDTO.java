package com.sep.comiverse.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageLiteDTO {
    private UUID pageId;
    private String imageUrl;
    private Integer pageNumber;
    private String status;
    private String bubbles;
}