package com.sep.comiverse.repository;

import com.sep.comiverse.entity.SubscriptionPlanEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ISubscriptionPlanRepository extends AbstractCrudRepository<SubscriptionPlanEntity, UUID> {
    List<SubscriptionPlanEntity> findAllByActiveTrueAndDeletedFalseOrderBySortOrderAscCreatedAtAsc();

    List<SubscriptionPlanEntity> findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc();

    Optional<SubscriptionPlanEntity> findByCodeIgnoreCaseAndDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndDeletedFalse(String code);
}
