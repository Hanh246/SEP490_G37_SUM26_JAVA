package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutCurrencyRateEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICreatorPayoutCurrencyRateRepository
        extends AbstractCrudRepository<CreatorPayoutCurrencyRateEntity, UUID> {

    Optional<CreatorPayoutCurrencyRateEntity> findByCurrencyCodeAndDeletedFalse(
            String currencyCode
    );

    Optional<CreatorPayoutCurrencyRateEntity> findByCurrencyCode(
            String currencyCode
    );

    List<CreatorPayoutCurrencyRateEntity> findAllByDeletedFalseOrderByCurrencyCodeAsc();
}
