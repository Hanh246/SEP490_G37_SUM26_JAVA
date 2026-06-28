package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChatFlagEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface IChatFlagRepository extends AbstractCrudRepository<ChatFlagEntity, UUID> {
}
