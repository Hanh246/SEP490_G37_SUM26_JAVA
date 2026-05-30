package com.sep.comiverse.common.dto.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PaginationMetadata {
    @JsonProperty("page")
    private final int page;
    @JsonProperty("size")
    private final int size;
    @JsonProperty("totalElements")
    private final long totalElements;
    @JsonProperty("totalPages")
    private final int totalPages;
    @JsonProperty("isLastPage")
    private boolean isLastPage;

    @PostConstruct
    private void init() {
        this.isLastPage = this.page == this.totalPages;
    }
}
