package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserSaveEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserSaveRepository extends AbstractCrudRepository<UserSaveEntity, UUID> {
    boolean existsByComicIdAndUserId(UUID comicId, UUID userId);
    Optional<UserSaveEntity> findByComicIdAndUserId(UUID comicId, UUID userId);
}
