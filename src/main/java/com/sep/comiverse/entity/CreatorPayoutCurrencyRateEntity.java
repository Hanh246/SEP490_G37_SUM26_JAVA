package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "creator_payout_supported_currencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_creator_payout_currency_code",
                columnNames = "currency_code"
        ),
        indexes = @Index(
                name = "idx_creator_payout_currency_active",
                columnList = "active"
        )
)
public class CreatorPayoutCurrencyRateEntity extends BaseEntity {

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "symbol", nullable = false, length = 8)
    private String symbol;

    /**
     * Number of payout-currency units represented by 1 USD.
     * Sandbox/manual rate. Examples: USD=1, EUR=0.92, CNY=7.20.
     */
    @Column(name = "units_per_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitsPerUsd;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
