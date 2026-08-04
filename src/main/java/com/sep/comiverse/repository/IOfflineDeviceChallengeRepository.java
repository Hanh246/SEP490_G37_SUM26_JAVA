package com.sep.comiverse.repository;

import com.sep.comiverse.entity.OfflineDeviceChallengeEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IOfflineDeviceChallengeRepository extends AbstractCrudRepository<OfflineDeviceChallengeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OfflineDeviceChallengeEntity> findByChallengeIdAndUserIdAndConsumedFalseAndDeletedFalse(UUID challengeId, UUID userId);
    long countByUserIdAndCreatedAtAfterAndDeletedFalse(UUID userId, Instant since);
}
