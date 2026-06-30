package com.sep.comiverse.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class TranslationRequestDTO {
    private UUID comicId;
    private String comicTitle;
    private String sourceLang;
    private List<String> targetLanguages;
    private String priority;
    private String deadline;
    private String notes;
}
