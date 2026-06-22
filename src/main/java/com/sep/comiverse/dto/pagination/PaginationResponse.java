package com.sep.comiverse.dto.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.dto.response.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaginationResponse<T> extends BaseResponse<T> {
    @JsonProperty("metadata")
    private PaginationMetadata metadata;
}
