package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumCommentLikeEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IForumCommentLikeRepository extends AbstractCrudRepository<ForumCommentLikeEntity, UUID> {
    Optional<ForumCommentLikeEntity> findByUserIdAndCommentId(UUID userId, UUID commentId);
    boolean existsByUserIdAndCommentId(UUID userId, UUID commentId);
    List<ForumCommentLikeEntity> findByUserId(UUID userId);

    @Query("SELECT fcl.commentId FROM ForumCommentLikeEntity fcl WHERE fcl.userId = :userId AND (fcl.deleted = false OR fcl.deleted IS NULL)")
    List<UUID> findLikedCommentIdsByUserId(@Param("userId") UUID userId);
}
