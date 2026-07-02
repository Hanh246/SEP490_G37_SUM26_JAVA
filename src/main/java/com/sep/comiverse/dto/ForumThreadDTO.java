package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumThreadDTO {
    private UUID id;
    private String title;
    private String author;
    private String category;
    private String content;
}
