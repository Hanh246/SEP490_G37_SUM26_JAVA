package com.sep.comiverse.repository;

import com.sep.comiverse.entity.LoginDeviceChallengeEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ILoginDeviceChallengeRepository extends AbstractCrudRepository<LoginDeviceChallengeEntity, UUID> {
    Optional<LoginDeviceChallengeEntity> findByChallengeIdAndConsumedFalseAndDeletedFalse(UUID challengeId);

    long countByUserIdAndCreatedAtAfterAndDeletedFalse(UUID userId, Instant createdAfter);
}
