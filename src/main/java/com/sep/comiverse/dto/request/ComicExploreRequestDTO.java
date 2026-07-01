package com.sep.comiverse.dto.request;

import com.sep.comiverse.constants.ComicStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComicExploreRequestDTO {
    private String cursor;

    private UUID referenceId;

    private List<UUID> genres;

    private ComicStatus status;

    private String sortBy = "DEFAULT";

    private int size = 15;
}
