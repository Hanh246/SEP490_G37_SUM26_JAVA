package com.sep.comiverse.dto.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;

@Data
@NoArgsConstructor
public class PaginationDTO {

    private final static int DEFAULT_PAGE_SIZE = 10;
    private final static int DEFAULT_PAGE = 1;

    @Schema(description = "Số trang", example = "1", defaultValue = "1")
    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @JsonProperty("page")
    private Integer page = DEFAULT_PAGE;

    @Schema(description = "Số phần tử trong 1 trang", example = "10", defaultValue = "10")
    @Min(value = 1, message = "1 trang phải có ít nhất 1 phần tử")
    @JsonProperty("size")
    private Integer size = DEFAULT_PAGE_SIZE;

    public PaginationDTO(Integer page, Integer size) {
        this.page = page != null && page >= 1 ? page : DEFAULT_PAGE;
        this.size = size != null && size >= 1 ? size : DEFAULT_PAGE_SIZE;
    }

    public PageRequest toPageRequest() {
        return PageRequest.of(page - 1, size, BaseEntity.getDefaultSorting());
    }
}
