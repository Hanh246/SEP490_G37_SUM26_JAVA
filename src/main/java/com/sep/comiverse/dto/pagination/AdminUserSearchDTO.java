package com.sep.comiverse.dto.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminUserSearchDTO extends PaginationSearchDTO {

    @Schema(description = "Filter by role name (e.g. USER, STAFF, ADMIN)")
    @JsonProperty("role")
    private String role;

    @Schema(description = "Filter by status (e.g. ACTIVE, INACTIVE)")
    @JsonProperty("status")
    private String status;
}
