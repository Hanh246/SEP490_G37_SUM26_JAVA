package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.BillingInterval;
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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "subscription_plans",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscription_plan_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_subscription_plan_active_sort", columnList = "active, sort_order"),
                @Index(name = "idx_subscription_plan_stripe_price", columnList = "stripe_price_id")
        }
)
public class SubscriptionPlanEntity extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval;

    @Builder.Default
    @Column(name = "interval_count", nullable = false)
    private Integer intervalCount = 1;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "recommended", nullable = false)
    private Boolean recommended = false;

    @Column(name = "badge", length = 80)
    private String badge;

    @Column(name = "features_json", columnDefinition = "text")
    private String featuresJson;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "stripe_product_id", length = 100)
    private String stripeProductId;

    @Column(name = "stripe_price_id", length = 100)
    private String stripePriceId;
}
