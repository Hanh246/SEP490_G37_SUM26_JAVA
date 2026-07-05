package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserLikeEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserLikeRepository extends AbstractCrudRepository<UserLikeEntity, UUID> {
    boolean existsByComicIdAndUserId(UUID comicId, UUID userId);
    Optional<UserLikeEntity> findByComicIdAndUserId(UUID comicId, UUID userId);
}
