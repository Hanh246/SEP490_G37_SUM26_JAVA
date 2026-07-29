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
public class PaymentLogResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private UUID planId;
    private String planCode;
    private String planName;
    private BigDecimal amount;
    private String currency;
    private PaymentTransactionStatus status;
    private String provider;
    private String stripeCheckoutSessionId;
    private String stripeSubscriptionId;
    private String stripeInvoiceId;
    private String failureReason;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}
