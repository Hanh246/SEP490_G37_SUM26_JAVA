package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumThreadEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IForumThreadRepository extends AbstractCrudRepository<ForumThreadEntity, UUID> {
    List<ForumThreadEntity> findByCategoryIgnoreCaseAndDeletedFalse(String category);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForumThreadEntity t SET t.views = COALESCE(t.views, 0) + 1 " +
            "WHERE t.id = :id AND t.deleted = false")
    int incrementViews(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ForumThreadEntity t WHERE t.id = :id AND t.deleted = false")
    Optional<ForumThreadEntity> findByIdForUpdate(@Param("id") UUID id);
}
