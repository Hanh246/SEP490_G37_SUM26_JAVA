package com.sep.comiverse.repository;

import com.sep.comiverse.entity.NotificationPreferenceEntity;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface INotificationPreferenceRepository extends AbstractCrudRepository<NotificationPreferenceEntity, UUID> {
    List<NotificationPreferenceEntity> findByUser_IdAndDeletedFalse(UUID userId);

    Optional<NotificationPreferenceEntity> findByUser_IdAndPreferenceKeyAndDeletedFalse(
            UUID userId,
            NotificationPreferenceKey preferenceKey
    );
}
