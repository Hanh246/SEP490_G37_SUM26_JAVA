package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComicDTO {
    private UUID id;
    private String title;
    private String author;
    private String projectTeam;
    private Integer chapters;
    private String views;
    private String status;
    private List<String> genres;
    private String cover;
}
