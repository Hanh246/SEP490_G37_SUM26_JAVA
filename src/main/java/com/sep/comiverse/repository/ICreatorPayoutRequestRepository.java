package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface ICreatorPayoutRequestRepository extends AbstractCrudRepository<CreatorPayoutRequestEntity, UUID> {

    List<CreatorPayoutRequestEntity> findAllByUserIdAndPayoutMonthAndDeletedFalseOrderByCreatedAtDesc(UUID userId, String payoutMonth);

    List<CreatorPayoutRequestEntity> findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(UUID userId);

    Page<CreatorPayoutRequestEntity> findAllByStatusAndDeletedFalse(CreatorPayoutStatus status, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(COALESCE(p.baseAmountUsd, p.amount)), 0)
            FROM CreatorPayoutRequestEntity p
            WHERE p.userId = :userId
              AND p.payoutMonth <= :throughMonth
              AND p.deleted = false
              AND p.status IN :statuses
            """)
    BigDecimal sumReservedThroughMonth(
            @Param("userId") UUID userId,
            @Param("throughMonth") String throughMonth,
            @Param("statuses") List<CreatorPayoutStatus> statuses
    );

    @Query("""
            SELECT COALESCE(SUM(COALESCE(p.baseAmountUsd, p.amount)), 0)
            FROM CreatorPayoutRequestEntity p
            WHERE p.userId = :userId
              AND p.role = :role
              AND p.status = :status
              AND p.deleted = false
            """)
    BigDecimal sumPaidAmountUsdByUserIdAndRole(
            @Param("userId") UUID userId,
            @Param("role") CreatorPayoutRole role,
            @Param("status") CreatorPayoutStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CreatorPayoutRequestEntity p WHERE p.id = :id AND p.deleted = false")
    Optional<CreatorPayoutRequestEntity> findLockedById(@Param("id") UUID id);
}
