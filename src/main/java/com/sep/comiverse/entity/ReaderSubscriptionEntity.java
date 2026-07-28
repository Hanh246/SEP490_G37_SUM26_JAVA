package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
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

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "reader_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reader_subscription_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_reader_subscription_stripe", columnNames = "stripe_subscription_id")
        },
        indexes = {
                @Index(name = "idx_reader_subscription_status", columnList = "status"),
                @Index(name = "idx_reader_subscription_period_end", columnList = "current_period_end")
        }
)
public class ReaderSubscriptionEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReaderSubscriptionStatus status;

    @Column(name = "stripe_customer_id", length = 120)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 120)
    private String stripeSubscriptionId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Builder.Default
    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "latest_payment_transaction_id")
    private UUID latestPaymentTransactionId;
}
