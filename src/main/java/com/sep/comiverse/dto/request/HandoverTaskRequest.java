package com.sep.comiverse.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class HandoverTaskRequest {

    private UUID newAssigneeId;

    /** Pages accepted as completed work of the previous translator. */
    private List<Integer> completedPageNumbers = new ArrayList<>();

    private BigDecimal responsibilityFactor = BigDecimal.ONE;

    private String reason;
}
