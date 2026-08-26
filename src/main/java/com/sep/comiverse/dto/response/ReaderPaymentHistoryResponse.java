package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderPaymentHistoryResponse {
    private UUID id;
    private String planCode;
    private String planName;
    private BigDecimal amount;
    private String currency;
    private PaymentTransactionStatus status;
    private String provider;
    private String failureReason;
    private Instant paidAt;
    private Instant createdAt;
}
