package com.sep.comiverse.repository;

import com.sep.comiverse.entity.OfflineLicenseEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface IOfflineLicenseRepository extends AbstractCrudRepository<OfflineLicenseEntity, UUID> {
    long countByUserIdAndCreatedAtAfterAndDeletedFalse(UUID userId, Instant since);

    @Modifying
    @Transactional
    @Query("UPDATE OfflineLicenseEntity l SET l.revoked = true, l.updatedAt = CURRENT_INSTANT " +
            "WHERE l.offlineDeviceId = :deviceId AND l.userId = :userId AND l.deleted = false")
    int revokeByDevice(@Param("deviceId") UUID deviceId, @Param("userId") UUID userId);
}
