package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorStripePayoutProfileEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICreatorStripePayoutProfileRepository
        extends AbstractCrudRepository<CreatorStripePayoutProfileEntity, UUID> {

    Optional<CreatorStripePayoutProfileEntity> findByUserIdAndDeletedFalse(UUID userId);

    Optional<CreatorStripePayoutProfileEntity> findByUserId(UUID userId);

    Optional<CreatorStripePayoutProfileEntity> findByStripeConnectedAccountIdAndDeletedFalse(
            String stripeConnectedAccountId
    );
}
