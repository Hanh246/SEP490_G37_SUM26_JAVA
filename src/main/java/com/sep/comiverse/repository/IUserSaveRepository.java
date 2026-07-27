package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserSaveEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserSaveRepository extends AbstractCrudRepository<UserSaveEntity, UUID> {
    boolean existsByComicIdAndUserId(UUID comicId, UUID userId);
    Optional<UserSaveEntity> findByComicIdAndUserId(UUID comicId, UUID userId);

    @org.springframework.data.jpa.repository.Query("SELECT us.comicId FROM UserSaveEntity us WHERE us.userId = :userId AND us.deleted = false ORDER BY us.updatedAt DESC")
    java.util.List<UUID> findSavedComicIdsByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(us) FROM UserSaveEntity us WHERE us.userId = :userId AND us.deleted = false")
    long countByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
