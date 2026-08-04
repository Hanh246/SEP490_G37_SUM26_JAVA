package com.sep.comiverse.repository;

import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICreatorPayoutSettingRepository extends AbstractCrudRepository<CreatorPayoutSettingEntity, UUID> {
    Optional<CreatorPayoutSettingEntity> findByConfigKey(String configKey);
    Optional<CreatorPayoutSettingEntity> findByConfigKeyAndDeletedFalse(String configKey);
}
