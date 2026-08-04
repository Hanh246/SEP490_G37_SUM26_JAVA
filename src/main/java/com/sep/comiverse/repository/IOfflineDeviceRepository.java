package com.sep.comiverse.repository;

import com.sep.comiverse.entity.OfflineDeviceEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IOfflineDeviceRepository extends AbstractCrudRepository<OfflineDeviceEntity, UUID> {
    Optional<OfflineDeviceEntity> findByUserIdAndDeviceIdHashAndDeletedFalse(UUID userId, String deviceIdHash);
    Optional<OfflineDeviceEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    List<OfflineDeviceEntity> findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndRevokedFalseAndDeletedFalse(UUID userId);
}
