package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.StripePayoutProfileStatus;
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

/**
 * Creator payout account and Stripe Connect profile in one table.
 * ComiVerse uses Stripe as its only payout provider, so a separate provider
 * profile table would duplicate the same one-to-one creator account data.
 *
 * Wallet balances are deliberately NOT persisted here; they are derived from
 * immutable earning entries minus reserved/paid payout requests to avoid drift.
 */
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
                @Index(name = "idx_creator_payout_account_status", columnList = "onboarding_status"),
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

    @Column(name = "account_country", length = 2)
    private String accountCountry;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "varchar(3) default 'USD'")
    private String currency = "USD";

    @Builder.Default
    @Column(name = "details_submitted", nullable = false, columnDefinition = "boolean default false")
    private Boolean detailsSubmitted = false;

    @Builder.Default
    @Column(name = "charges_enabled", nullable = false, columnDefinition = "boolean default false")
    private Boolean chargesEnabled = false;

    @Builder.Default
    @Column(name = "payouts_enabled", nullable = false, columnDefinition = "boolean default false")
    private Boolean payoutsEnabled = false;

    @Column(name = "transfers_capability", length = 30)
    private String transfersCapability;

    @Column(name = "requirements_currently_due", length = 2000)
    private String requirementsCurrentlyDue;

    @Column(name = "requirements_disabled_reason", length = 255)
    private String requirementsDisabledReason;

    @Column(name = "external_account_type", length = 40)
    private String externalAccountType;

    @Column(name = "external_account_last4", length = 4)
    private String externalAccountLast4;

    @Column(name = "external_account_display_name", length = 120)
    private String externalAccountDisplayName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "onboarding_status", nullable = false, length = 40, columnDefinition = "varchar(40) default 'CREATED'")
    private StripePayoutProfileStatus onboardingStatus = StripePayoutProfileStatus.CREATED;

    @Builder.Default
    @Column(name = "active", nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;
}
