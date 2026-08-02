package com.sep.comiverse.repository;

import com.sep.comiverse.entity.NotificationEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public interface INotificationRepository extends AbstractCrudRepository<NotificationEntity, UUID> {

    /**
     * Get distinct broadcast summaries — one row per broadcastId.
     * Returns: broadcastId, type, title, message, targetRoles, recipientCount, sentAt
     */
    @Query("SELECT n.broadcastId, n.type, n.title, n.message, n.targetRoles, COUNT(n), MIN(n.createdAt) " +
           "FROM NotificationEntity n " +
           "WHERE n.broadcastId IS NOT NULL AND n.deleted = false " +
           "GROUP BY n.broadcastId, n.type, n.title, n.message, n.targetRoles " +
           "ORDER BY MIN(n.createdAt) DESC")
    List<Object[]> findBroadcastSummaries();

    @Query("SELECT n FROM NotificationEntity n WHERE n.user.id = :userId AND n.deleted = false ORDER BY n.createdAt DESC")
    List<NotificationEntity> findByUserId(UUID userId);

    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.user.id = :userId AND n.isRead = false AND n.deleted = false")
    long countUnreadByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE NotificationEntity n SET n.deleted = true, n.updatedAt = CURRENT_INSTANT WHERE n.broadcastId = :broadcastId")
    void softDeleteByBroadcastId(@org.springframework.data.repository.query.Param("broadcastId") UUID broadcastId);
}
