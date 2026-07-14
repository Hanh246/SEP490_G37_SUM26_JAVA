package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterUploadRequest {

    private UUID authorId;
    
    private String chapterNumber;

    private String title;
}