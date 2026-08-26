package com.sep.comiverse.repository;

import com.sep.comiverse.dto.UserSnapshot;
import com.sep.comiverse.entity.UserEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserRepository extends AbstractCrudRepository<UserEntity, UUID> {
    long countByStatusIgnoreCaseAndDeletedFalse(String status);

    @Query("""
        SELECT r.roleName, COUNT(u)
        FROM UserEntity u
        LEFT JOIN u.role r
        WHERE u.deleted = false
        GROUP BY r.roleName
        """)
    List<Object[]> countUsersByRole();

    @Query("""
        SELECT new com.sep.comiverse.dto.UserSnapshot(u.id, u.fullName, u.avatarUrl)
        FROM UserEntity u
        WHERE u.id = :id
        AND u.deleted = false
        """)
    Optional<UserSnapshot> findUserSnapshotById(@Param("id") UUID id);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role WHERE u.id = :id AND u.deleted = false")
    Optional<UserEntity> findByIdWithRole(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id AND u.deleted = false")
    Optional<UserEntity> lockById(@Param("id") UUID id);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role WHERE u.username = :username AND u.deleted = false")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role WHERE LOWER(u.email) = LOWER(:email) AND u.deleted = false")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role WHERE (u.username = :username OR LOWER(u.email) = LOWER(:email)) AND u.deleted = false")
    Optional<UserEntity> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    @Query("SELECT u FROM UserEntity u JOIN u.role r WHERE LOWER(r.roleName) = 'translator' " +
           "AND (u.deleted = false OR u.deleted IS NULL) " +
           "AND (u.status = 'ACTIVE' OR u.status IS NULL) " +
           "AND (:query IS NULL OR :query = '' " +
           "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    java.util.List<UserEntity> searchTranslators(@Param("query") String query);

    @Query("SELECT u FROM UserEntity u JOIN u.role r WHERE LOWER(r.roleName) = 'project_leader' " +
           "AND (u.deleted = false OR u.deleted IS NULL) " +
           "AND (u.status = 'ACTIVE' OR u.status IS NULL) " +
           "AND (:query IS NULL OR :query = '' " +
           "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    java.util.List<UserEntity> searchProjectLeaders(@Param("query") String query);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.username = :username AND u.deleted = false")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE LOWER(u.email) = LOWER(:email) AND u.deleted = false")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.phone = :phone AND u.deleted = false")
    boolean existsByPhone(@Param("phone") String phone);

    @SuppressWarnings("JpaQlInspection")
    @Query("""
            SELECT DISTINCT u.id FROM UserEntity u
            WHERE u.deleted = false AND (
                u.userVector IS NULL OR
                u.vectorUpdatedAt IS NULL OR
                EXISTS (SELECT 1 FROM UserLikeEntity l WHERE l.userId = u.id AND l.updatedAt > u.vectorUpdatedAt) OR
                EXISTS (SELECT 1 FROM UserSaveEntity s WHERE s.userId = u.id AND s.updatedAt > u.vectorUpdatedAt) OR
                EXISTS (SELECT 1 FROM ReadingHistoryEntity r WHERE r.userId = u.id AND r.updatedAt > u.vectorUpdatedAt)
            )
            """)
    List<UUID> findUserIdsWithPendingVectorUpdate();

    @Query("SELECT u FROM UserEntity u WHERE (LOWER(u.username) = LOWER(:lookup) OR LOWER(u.fullName) = LOWER(:lookup)) AND u.deleted = false")
    List<UserEntity> findByUsernameOrFullNameIgnoreCase(@Param("lookup") String lookup);

    @Modifying
    @Transactional
    @Query("UPDATE UserEntity u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    int updateLastSeenAt(@Param("userId") UUID userId, @Param("lastSeenAt") Instant lastSeenAt);
}
