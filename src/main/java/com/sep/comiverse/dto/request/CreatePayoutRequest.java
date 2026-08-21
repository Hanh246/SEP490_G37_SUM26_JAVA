package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
            message = "Payout month must use YYYY-MM format"
    )
    private String payoutMonth;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "0.01", message = "Requested amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Requested amount must have at most 2 decimal places")
    private BigDecimal requestedAmount;

    @Pattern(
            regexp = "^USD$",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Payout currency must be USD"
    )
    private String payoutCurrency;

    @Size(max = 500, message = "Request note must be at most 500 characters")
    private String note;
}
