package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComicExploreRequestDTO {
    @Size(max = 100)
    private String search;
    private String cursor;
    private UUID referenceId;
    private List<UUID> genres;
    private ComicPublicationStatus publicationStatus;
    private String sortBy = "DEFAULT";
    private int size = 15;
}
