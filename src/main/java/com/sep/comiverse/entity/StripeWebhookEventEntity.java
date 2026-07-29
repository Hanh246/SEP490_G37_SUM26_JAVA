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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "stripe_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_stripe_webhook_event", columnNames = "event_id"),
        indexes = @Index(name = "idx_stripe_webhook_type_created", columnList = "event_type, create_at")
)
public class StripeWebhookEventEntity extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Builder.Default
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "error_message", length = 1500)
    private String errorMessage;
}
