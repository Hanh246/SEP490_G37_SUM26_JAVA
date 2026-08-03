package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
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
import java.util.UUID;

@Repository
public interface ICreatorPayoutRequestRepository extends AbstractCrudRepository<CreatorPayoutRequestEntity, UUID> {

    Optional<CreatorPayoutRequestEntity> findByUserIdAndPayoutMonthAndDeletedFalse(UUID userId, String payoutMonth);

    List<CreatorPayoutRequestEntity> findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(UUID userId);

    Page<CreatorPayoutRequestEntity> findAllByStatusAndDeletedFalse(CreatorPayoutStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CreatorPayoutRequestEntity p WHERE p.id = :id AND p.deleted = false")
    Optional<CreatorPayoutRequestEntity> findLockedById(@Param("id") UUID id);
}
