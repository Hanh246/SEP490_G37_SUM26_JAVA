package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;

@Repository
public interface IReaderSubscriptionRepository extends AbstractCrudRepository<ReaderSubscriptionEntity, UUID> {
    Optional<ReaderSubscriptionEntity> findByUserIdAndDeletedFalse(UUID userId);

    Optional<ReaderSubscriptionEntity> findByStripeSubscriptionIdAndDeletedFalse(String subscriptionId);

    long countByDeletedFalseAndStatusInAndCurrentPeriodEndAfter(
            Collection<ReaderSubscriptionStatus> statuses,
            Instant currentTime
    );
}
