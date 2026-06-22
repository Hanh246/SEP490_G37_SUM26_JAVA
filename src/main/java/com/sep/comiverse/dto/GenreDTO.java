package com.sep.comiverse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenreDTO {
    private UUID id;

    @NotBlank
    private String name;

    @NotBlank
    private String slug;
}
