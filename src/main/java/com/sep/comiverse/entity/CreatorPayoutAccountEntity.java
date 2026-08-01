package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
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
        name = "creator_payout_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_creator_payout_account_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_creator_payout_account_stripe", columnNames = "stripe_connected_account_id")
        },
        indexes = {
                @Index(name = "idx_creator_payout_account_role", columnList = "role"),
                @Index(name = "idx_creator_payout_account_stripe", columnList = "stripe_connected_account_id")
        }
)
public class CreatorPayoutAccountEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CreatorPayoutRole role;

    @Column(name = "stripe_connected_account_id", nullable = false, length = 120)
    private String stripeConnectedAccountId;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "account_country", length = 2)
    private String accountCountry;

    @Column(name = "transfers_capability", length = 30)
    private String transfersCapability;

    @Builder.Default
    @Column(name = "payouts_enabled", nullable = false)
    private Boolean payoutsEnabled = false;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
