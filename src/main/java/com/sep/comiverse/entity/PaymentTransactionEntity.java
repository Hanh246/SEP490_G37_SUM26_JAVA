package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "payment_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_transaction_checkout_session",
                columnNames = "stripe_checkout_session_id"
        ),
        indexes = {
                @Index(name = "idx_payment_transaction_user", columnList = "user_id, create_at"),
                @Index(name = "idx_payment_transaction_status", columnList = "status, create_at"),
                @Index(name = "idx_payment_transaction_subscription", columnList = "stripe_subscription_id")
        }
)
public class PaymentTransactionEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentTransactionStatus status;

    @Builder.Default
    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "STRIPE";

    @Column(name = "stripe_checkout_session_id", length = 120)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", length = 120)
    private String stripePaymentIntentId;

    @Column(name = "stripe_subscription_id", length = 120)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id", length = 120)
    private String stripeCustomerId;

    @Column(name = "stripe_invoice_id", length = 120)
    private String stripeInvoiceId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;
}
