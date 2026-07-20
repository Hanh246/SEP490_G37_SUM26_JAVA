package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumCommentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IForumCommentRepository extends AbstractCrudRepository<ForumCommentEntity, UUID> {
    List<ForumCommentEntity> findByThreadIdAndDeletedFalseOrderByCreatedAtAsc(UUID threadId);
}
