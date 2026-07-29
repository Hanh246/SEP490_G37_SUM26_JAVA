package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutStatusResponse {
    private String sessionId;
    private PaymentTransactionStatus paymentStatus;
    private String planCode;
    private String planName;
    private Boolean premiumActive;
    private Instant premiumExpiresAt;
}
