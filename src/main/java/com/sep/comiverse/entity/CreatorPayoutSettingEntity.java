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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "creator_payout_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_creator_payout_setting_key", columnNames = "config_key")
})
public class CreatorPayoutSettingEntity extends BaseEntity {

    @Builder.Default
    @Column(name = "config_key", nullable = false, length = 40)
    private String configKey = "DEFAULT";

    /**
     * Rewards and limits are accounted for in USD. Transfers may be settled in
     * USD, EUR, or CNY using an administrator-managed sandbox conversion rate.
     * Legacy database column names are retained to avoid destructive schema changes.
     */
    @Builder.Default
    @Column(name = "minimum_payout_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumPayoutUsd = new BigDecimal("10.00");

    @Builder.Default
    @Column(name = "translator_task_rate_vnd", nullable = false, precision = 19, scale = 2)
    /** Legacy field name; value is the default USD remuneration per translated page. */
    private BigDecimal translatorTaskRateUsd = new BigDecimal("1.20");

    @Builder.Default
    @Column(name = "translator_monthly_limit_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal translatorMonthlyLimitUsd = new BigDecimal("200.00");

    @Builder.Default
    @Column(name = "author_views_per_unit", nullable = false)
    private Long authorViewsPerUnit = 1_000L;

    @Builder.Default
    @Column(name = "author_view_unit_rate_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorViewUnitRateUsd = new BigDecimal("40.00");

    @Builder.Default
    @Column(name = "author_follows_per_unit", nullable = false)
    private Long authorFollowsPerUnit = 100L;

    @Builder.Default
    @Column(name = "author_follow_unit_rate_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorFollowUnitRateUsd = new BigDecimal("40.00");

    @Builder.Default
    @Column(name = "author_monthly_limit_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorMonthlyLimitUsd = new BigDecimal("480.00");

    /** Marker used to make the legacy VND-to-USD migration idempotent. */
    @Builder.Default
    @Column(name = "currency", length = 3)
    private String currency = "USD";
}
