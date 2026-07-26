package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserRatingEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserRatingRepository extends AbstractCrudRepository<UserRatingEntity, UUID> {

    boolean existsByComicIdAndUserIdAndDeletedFalse(UUID comicId, UUID userId);

    Optional<UserRatingEntity> findByComicIdAndUserIdAndDeletedFalse(UUID comicId, UUID userId);

    Optional<UserRatingEntity> findByComicIdAndUserId(UUID comicId, UUID userId);

    @Query("SELECT AVG(ur.score) FROM UserRatingEntity ur WHERE ur.comicId = :comicId AND ur.deleted = false")
    Double getAverageRatingByComicId(@Param("comicId") UUID comicId);

    @Query("SELECT COUNT(ur) FROM UserRatingEntity ur WHERE ur.comicId = :comicId AND ur.deleted = false")
    Integer getRatingCountByComicId(@Param("comicId") UUID comicId);

    @Query("SELECT ur.comicId FROM UserRatingEntity ur WHERE ur.userId = :userId AND ur.deleted = false ORDER BY ur.updatedAt DESC")
    List<UUID> findRatedComicIdsByUserId(@Param("userId") UUID userId);
}
