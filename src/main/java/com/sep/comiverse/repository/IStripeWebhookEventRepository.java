package com.sep.comiverse.repository;

import com.sep.comiverse.entity.StripeWebhookEventEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IStripeWebhookEventRepository extends AbstractCrudRepository<StripeWebhookEventEntity, UUID> {
    boolean existsByEventIdAndDeletedFalse(String eventId);

    Optional<StripeWebhookEventEntity> findByEventIdAndDeletedFalse(String eventId);
}
