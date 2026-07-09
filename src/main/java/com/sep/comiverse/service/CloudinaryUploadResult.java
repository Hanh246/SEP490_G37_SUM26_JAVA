package com.sep.comiverse.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudinaryUploadResult {
    private String secureUrl;
    private String publicId;
    private Integer width;
    private Integer height;
    private Long bytes;
}
