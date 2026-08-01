package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPayoutPageResponse {
    private List<CreatorPayoutRequestResponse> items;
    private Map<CreatorPayoutStatus, Long> counts;
    private Map<CreatorPayoutStatus, BigDecimal> totals;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
