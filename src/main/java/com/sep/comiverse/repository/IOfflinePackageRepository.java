package com.sep.comiverse.repository;

import com.sep.comiverse.entity.OfflinePackageEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IOfflinePackageRepository extends AbstractCrudRepository<OfflinePackageEntity, UUID> {
    Optional<OfflinePackageEntity> findByPackageIdAndUserIdAndRevokedFalseAndDeletedFalse(UUID packageId, UUID userId);
    long countByUserIdAndCreatedAtAfterAndDeletedFalse(UUID userId, Instant since);

    @Modifying
    @Transactional
    @Query("UPDATE OfflinePackageEntity p SET p.revoked = true, p.updatedAt = CURRENT_INSTANT " +
            "WHERE p.offlineDeviceId = :deviceId AND p.userId = :userId AND p.deleted = false")
    int revokeByDevice(@Param("deviceId") UUID deviceId, @Param("userId") UUID userId);
}
