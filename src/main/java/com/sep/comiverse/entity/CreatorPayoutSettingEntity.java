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

    @Builder.Default
    @Column(name = "minimum_payout_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumPayoutVnd = BigDecimal.valueOf(50_000);

    @Builder.Default
    @Column(name = "translator_task_rate_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal translatorTaskRateVnd = BigDecimal.valueOf(50_000);

    @Builder.Default
    @Column(name = "translator_monthly_limit_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal translatorMonthlyLimitVnd = BigDecimal.valueOf(5_000_000);

    @Builder.Default
    @Column(name = "author_views_per_unit", nullable = false)
    private Long authorViewsPerUnit = 1_000L;

    @Builder.Default
    @Column(name = "author_view_unit_rate_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorViewUnitRateVnd = BigDecimal.valueOf(1_000_000);

    @Builder.Default
    @Column(name = "author_follows_per_unit", nullable = false)
    private Long authorFollowsPerUnit = 100L;

    @Builder.Default
    @Column(name = "author_follow_unit_rate_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorFollowUnitRateVnd = BigDecimal.valueOf(1_000_000);

    @Builder.Default
    @Column(name = "author_monthly_limit_vnd", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorMonthlyLimitVnd = BigDecimal.valueOf(12_000_000);
}
