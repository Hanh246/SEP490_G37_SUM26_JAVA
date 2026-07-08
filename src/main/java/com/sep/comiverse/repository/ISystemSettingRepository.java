package com.sep.comiverse.repository;

import com.sep.comiverse.entity.SystemSettingEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ISystemSettingRepository extends AbstractCrudRepository<SystemSettingEntity, UUID> {
    Optional<SystemSettingEntity> findBySettingKey(String settingKey);
}
