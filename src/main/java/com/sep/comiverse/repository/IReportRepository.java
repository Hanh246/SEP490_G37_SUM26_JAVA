package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReportEntity;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IReportRepository extends JpaRepository<ReportEntity, UUID>, JpaSpecificationExecutor<ReportEntity> {

    @Query("SELECT r FROM ReportEntity r " +
            "LEFT JOIN FETCH r.reporter " +
            "LEFT JOIN FETCH r.category " +
            "LEFT JOIN FETCH r.handler " +
            "WHERE r.id = :id AND (r.deleted = false OR r.deleted IS NULL)")
    Optional<ReportEntity> findByIdWithDetails(@Param("id") UUID id);

    Optional<ReportEntity> findByIdAndDeletedFalse(UUID id);

    boolean existsByReporter_IdAndTargetTypeAndTargetIdAndStatusInAndDeletedFalse(
            UUID reporterId,
            ReportTargetType targetType,
            UUID targetId,
            Collection<ReportStatus> statuses
    );

    Page<ReportEntity> findAllByReporter_IdAndDeletedFalse(UUID reporterId, Pageable pageable);

    long countByStatusAndDeletedFalse(ReportStatus status);

    long countByCategory_AssignedRoleAndStatusInAndDeletedFalse(
            ReportAssignedRole assignedRole,
            Collection<ReportStatus> statuses
    );

    Optional<ReportEntity> findFirstByTargetTypeAndTargetIdAndStatusAndDeletedFalseOrderByResolvedAtDesc(
            ReportTargetType targetType,
            UUID targetId,
            ReportStatus status
    );

    List<ReportEntity> findByTargetTypeAndTargetIdInAndStatusAndDeletedFalseOrderByResolvedAtDesc(
            ReportTargetType targetType,
            Collection<UUID> targetIds,
            ReportStatus status
    );
}
