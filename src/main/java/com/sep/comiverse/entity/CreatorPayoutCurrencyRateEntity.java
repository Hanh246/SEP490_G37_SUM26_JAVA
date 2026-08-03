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
@Table(name = "creator_payout_currency_rates",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_currency_country", columnNames = "country_code"),
        indexes = @Index(name = "idx_payout_currency_code", columnList = "currency_code"))
public class CreatorPayoutCurrencyRateEntity extends BaseEntity {

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Number of VND represented by one unit of currency_code. Test/manual rate, not live FX. */
    @Column(name = "vnd_per_unit", nullable = false, precision = 19, scale = 6)
    private BigDecimal vndPerUnit;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
