package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutCurrencyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICreatorPayoutCurrencyRepository
        extends AbstractCrudRepository<CreatorPayoutCurrencyEntity, UUID> {

    Optional<CreatorPayoutCurrencyEntity> findByCurrencyCodeAndDeletedFalse(String currencyCode);

    Optional<CreatorPayoutCurrencyEntity> findByCurrencyCode(String currencyCode);

    List<CreatorPayoutCurrencyEntity> findAllByDeletedFalseOrderByCurrencyCodeAsc();
}
