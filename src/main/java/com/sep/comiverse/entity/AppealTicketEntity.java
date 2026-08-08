package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.AppealStatus;
import com.sep.comiverse.entity.enums.AppealTargetType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "appeal_tickets", indexes = {
        @Index(name = "idx_appeal_tickets_status_deleted", columnList = "status, deleted"),
        @Index(name = "idx_appeal_tickets_author_target", columnList = "author_id, target_id")
})
public class AppealTicketEntity extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private AppealTargetType targetType;

    @Column(name = "appeal_reason", columnDefinition = "TEXT", nullable = false)
    private String appealReason;

    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    private String evidenceUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppealStatus status = AppealStatus.PENDING;

    @Column(name = "resolved_by_mod_id")
    private UUID resolvedByModId;

    @Column(name = "resolved_reason", columnDefinition = "TEXT")
    private String resolvedReason;

    @Column(name = "previous_state_snapshot", columnDefinition = "TEXT")
    private String previousStateSnapshot;
}
