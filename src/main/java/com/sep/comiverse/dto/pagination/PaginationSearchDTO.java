package com.sep.comiverse.dto.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaginationSearchDTO extends PaginationDTO {
    @Schema(description = "Search keyword")
    @JsonProperty("search")
    private String search = "";

    public PaginationSearchDTO(String search, Integer page, Integer size) {
        super(page, size);
        this.search = search;
    }
}
