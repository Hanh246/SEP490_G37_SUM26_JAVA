package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "report_categories", indexes = {
        @Index(name = "idx_report_categories_active", columnList = "is_active, deleted"),
        @Index(name = "idx_report_categories_role", columnList = "assigned_role, is_active, deleted")
})
@EqualsAndHashCode(callSuper = true, exclude = "createdBy")
@ToString(exclude = "createdBy")
public class ReportCategoryEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, length = 32)
    private ReportAssignedRole assignedRole;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_types", columnDefinition = "text[]", nullable = false)
    private List<ReportTargetType> targetTypes = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    public boolean supportsTargetType(ReportTargetType targetType) {
        if (targetType == null) return true;
        if (this.targetTypes == null || this.targetTypes.isEmpty()) return true;
        return this.targetTypes.contains(targetType);
    }
}
