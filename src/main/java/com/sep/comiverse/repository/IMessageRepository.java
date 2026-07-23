package com.sep.comiverse.repository;

import com.sep.comiverse.entity.MessageEntity;
import com.sep.comiverse.entity.enums.ChatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IMessageRepository extends AbstractCrudRepository<MessageEntity, UUID> {

    @Query(
        value = "SELECT m FROM MessageEntity m WHERE m.chatType = :chatType AND m.deleted = false ORDER BY m.createdAt DESC",
        countQuery = "SELECT COUNT(m) FROM MessageEntity m WHERE m.chatType = :chatType AND m.deleted = false"
    )
    Page<MessageEntity> findByChatType(@Param("chatType") ChatType chatType, Pageable pageable);

    @Query(
        value = "SELECT m FROM MessageEntity m WHERE m.chatType = :chatType AND m.groupId = :groupId AND m.deleted = false ORDER BY m.createdAt DESC",
        countQuery = "SELECT COUNT(m) FROM MessageEntity m WHERE m.chatType = :chatType AND m.groupId = :groupId AND m.deleted = false"
    )
    Page<MessageEntity> findByChatTypeAndGroupId(@Param("chatType") ChatType chatType, @Param("groupId") UUID groupId, Pageable pageable);
}
