package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserLikeEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserLikeRepository extends AbstractCrudRepository<UserLikeEntity, UUID> {
    boolean existsByComicIdAndUserId(UUID comicId, UUID userId);
    Optional<UserLikeEntity> findByComicIdAndUserId(UUID comicId, UUID userId);

    @Query("SELECT ul.comicId FROM UserLikeEntity ul WHERE ul.userId = :userId AND ul.deleted = false ORDER BY ul.updatedAt DESC")
    List<UUID> findLikedComicIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(ul) FROM UserLikeEntity ul WHERE ul.userId = :userId AND ul.deleted = false")
    long countByUserId(@Param("userId") UUID userId);
}
