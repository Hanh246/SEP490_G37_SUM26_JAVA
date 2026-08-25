package com.sep.comiverse.repository;

import com.sep.comiverse.entity.PushDeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IPushDeviceTokenRepository extends JpaRepository<PushDeviceTokenEntity, UUID> {

    Optional<PushDeviceTokenEntity> findByToken(String token);

    @Query("""
            SELECT device
            FROM PushDeviceTokenEntity device
            WHERE device.user.id = :userId
              AND device.enabled = true
              AND (device.deleted = false OR device.deleted IS NULL)
            """)
    List<PushDeviceTokenEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT device
            FROM PushDeviceTokenEntity device
            JOIN FETCH device.user user
            WHERE user.id IN :userIds
              AND device.enabled = true
              AND (device.deleted = false OR device.deleted IS NULL)
            """)
    List<PushDeviceTokenEntity> findActiveByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("""
            SELECT COUNT(device)
            FROM PushDeviceTokenEntity device
            WHERE device.user.id = :userId
              AND device.enabled = true
              AND (device.deleted = false OR device.deleted IS NULL)
            """)
    long countActiveByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PushDeviceTokenEntity device WHERE device.token IN :tokens")
    int deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);
}
