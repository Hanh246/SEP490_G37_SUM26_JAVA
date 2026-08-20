package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "task_handovers", indexes = {
        @Index(name = "idx_task_handover_task", columnList = "task_id, handed_over_at")
})
public class TaskHandoverEntity extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "from_translator_id", nullable = false)
    private UUID fromTranslatorId;

    @Column(name = "to_translator_id", nullable = false)
    private UUID toTranslatorId;

    @Column(name = "performed_by_id", nullable = false)
    private UUID performedById;

    @Column(name = "handed_over_at", nullable = false)
    private Instant handedOverAt;

    @Column(name = "responsibility_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal responsibilityFactor;

    @Column(name = "accepted_page_count", nullable = false)
    private Integer acceptedPageCount;

    @Column(name = "reassigned_page_count", nullable = false)
    private Integer reassignedPageCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_page_numbers", nullable = false, columnDefinition = "jsonb")
    private String acceptedPageNumbers;

    @Column(name = "reason", length = 1000)
    private String reason;
}
