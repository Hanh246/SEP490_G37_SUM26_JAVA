package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {

    @NotBlank(message = "Payout month is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Payout month must use YYYY-MM format")
    private String payoutMonth;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "0.01", message = "Requested amount must be greater than zero")
    private BigDecimal requestedAmount;

    @Size(max = 500, message = "Request note must be at most 500 characters")
    private String note;
}
