package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row per payout currency. Supported-currency configuration and the
 * conversion rate are intentionally kept together because ComiVerse only
 * needs the current manual/sandbox rate for Stripe payouts.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "creator_payout_currencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_creator_payout_currency_code",
                columnNames = "currency_code"
        )
)
public class CreatorPayoutCurrencyEntity extends BaseEntity {

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "symbol", nullable = false, length = 8)
    private String symbol;

    /** Number of payout-currency units represented by 1 USD. */
    @Column(name = "units_per_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitsPerUsd;

    @Builder.Default
    @Column(name = "active", nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;
}
