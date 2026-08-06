package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class HandoverTaskRequest {

    @NotNull
    private UUID newAssigneeId;

    /** Pages accepted as completed work of the previous translator. */
    private List<Integer> completedPageNumbers = new ArrayList<>();

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "1.00")
    private BigDecimal responsibilityFactor = BigDecimal.ONE;

    @NotBlank
    private String reason;
}
