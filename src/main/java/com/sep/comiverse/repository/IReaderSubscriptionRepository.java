package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IReaderSubscriptionRepository extends AbstractCrudRepository<ReaderSubscriptionEntity, UUID> {
    Optional<ReaderSubscriptionEntity> findByUserIdAndDeletedFalse(UUID userId);

    Optional<ReaderSubscriptionEntity> findByStripeSubscriptionIdAndDeletedFalse(String subscriptionId);
}
