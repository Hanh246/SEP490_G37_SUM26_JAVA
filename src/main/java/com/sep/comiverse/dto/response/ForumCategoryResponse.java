package com.sep.comiverse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ForumCategoryResponse {
    private UUID id;
    private String name;
    private String color;
}
