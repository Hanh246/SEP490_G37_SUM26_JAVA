package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
        name = "creator_payout_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_creator_payout_user_month",
                        columnNames = {"user_id", "payout_month"}
                ),
                @UniqueConstraint(
                        name = "uk_creator_payout_stripe_transfer",
                        columnNames = "stripe_transfer_id"
                )
        },
        indexes = {
                @Index(name = "idx_creator_payout_user_created", columnList = "user_id, create_at"),
                @Index(name = "idx_creator_payout_status_created", columnList = "status, create_at"),
                @Index(name = "idx_creator_payout_month", columnList = "payout_month")
        }
)
public class CreatorPayoutRequestEntity extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CreatorPayoutRole role;

    @Column(name = "payout_month", nullable = false, length = 7)
    private String payoutMonth;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "gross_amount_vnd", precision = 19, scale = 2)
    private BigDecimal grossAmountVnd;

    @Column(name = "base_amount_vnd", precision = 19, scale = 2)
    private BigDecimal baseAmountVnd;

    @Column(name = "monthly_limit_vnd", precision = 19, scale = 2)
    private BigDecimal monthlyLimitVnd;

    @Column(name = "exchange_rate_vnd_per_unit", precision = 19, scale = 6)
    private BigDecimal exchangeRateVndPerUnit;

    @Column(name = "account_country", length = 2)
    private String accountCountry;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreatorPayoutStatus status;

    @Column(name = "stripe_connected_account_id", nullable = false, length = 120)
    private String stripeConnectedAccountId;

    @Column(name = "stripe_transfer_id", length = 120)
    private String stripeTransferId;

    @Column(name = "request_note", length = 500)
    private String requestNote;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "calculation_details", length = 1000)
    private String calculationDetails;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "failed_at")
    private Instant failedAt;
}
