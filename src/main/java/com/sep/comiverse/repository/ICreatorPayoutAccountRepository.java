package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICreatorPayoutAccountRepository
        extends AbstractCrudRepository<CreatorPayoutAccountEntity, UUID> {

    Optional<CreatorPayoutAccountEntity> findByUserIdAndDeletedFalse(UUID userId);

    Optional<CreatorPayoutAccountEntity> findByUserId(UUID userId);

    Optional<CreatorPayoutAccountEntity> findByStripeConnectedAccountIdAndDeletedFalse(
            String stripeConnectedAccountId
    );
}
