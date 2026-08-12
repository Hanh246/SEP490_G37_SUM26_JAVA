package com.sep.comiverse.repository;

import com.sep.comiverse.entity.LoginDeviceEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ILoginDeviceRepository extends AbstractCrudRepository<LoginDeviceEntity, UUID> {
    Optional<LoginDeviceEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    Optional<LoginDeviceEntity> findByUserIdAndDeviceIdHashAndDeletedFalse(UUID userId, String deviceIdHash);

    long countByUserIdAndRevokedFalseAndDeletedFalse(UUID userId);

    List<LoginDeviceEntity> findAllByUserIdAndDeletedFalseOrderByLastSeenAtDesc(UUID userId);

    List<LoginDeviceEntity> findAllByUserIdAndRevokedFalseAndDeletedFalseOrderByLastSeenAtDesc(UUID userId);

    @Query("""
            SELECT CASE WHEN COUNT(device) > 0 THEN true ELSE false END
            FROM LoginDeviceEntity device
            WHERE device.id = :deviceId
              AND device.user.id = :userId
              AND device.revoked = false
              AND device.deleted = false
            """)
    boolean isActive(@Param("deviceId") UUID deviceId, @Param("userId") UUID userId);
}
