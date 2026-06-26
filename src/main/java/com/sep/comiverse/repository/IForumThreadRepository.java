package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ForumThreadEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface IForumThreadRepository extends AbstractCrudRepository<ForumThreadEntity, UUID> {
}
