package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumThreadLikeEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IForumThreadLikeRepository extends AbstractCrudRepository<ForumThreadLikeEntity, UUID> {

    Optional<ForumThreadLikeEntity> findByUserIdAndThreadId(UUID userId, UUID threadId);

    @Query("SELECT ftl.threadId FROM ForumThreadLikeEntity ftl " +
            "WHERE ftl.userId = :userId AND (ftl.deleted = false OR ftl.deleted IS NULL)")
    List<UUID> findLikedThreadIdsByUserId(@Param("userId") UUID userId);
}
