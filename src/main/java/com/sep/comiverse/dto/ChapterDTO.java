package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDTO {
    private UUID id;
    private String num;
    private String date;
    private Integer words;
    private String content;
}
