package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumThreadFollowEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IForumThreadFollowRepository extends AbstractCrudRepository<ForumThreadFollowEntity, UUID> {

    Optional<ForumThreadFollowEntity> findByUserIdAndThreadId(UUID userId, UUID threadId);

    @Query("SELECT ftf.threadId FROM ForumThreadFollowEntity ftf " +
            "WHERE ftf.userId = :userId AND (ftf.deleted = false OR ftf.deleted IS NULL)")
    List<UUID> findFollowedThreadIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT ftf.userId FROM ForumThreadFollowEntity ftf " +
            "WHERE ftf.threadId = :threadId AND (ftf.deleted = false OR ftf.deleted IS NULL)")
    List<UUID> findFollowerUserIdsByThreadId(@Param("threadId") UUID threadId);
}
