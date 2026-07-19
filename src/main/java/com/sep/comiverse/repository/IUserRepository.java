package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserRepository extends AbstractCrudRepository<UserEntity, UUID> {
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.role WHERE u.id = :id AND u.deleted = false")
    Optional<UserEntity> findByIdWithRole(@Param("id") UUID id);

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

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.username = :username AND u.deleted = false")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE LOWER(u.email) = LOWER(:email) AND u.deleted = false")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.phone = :phone AND u.deleted = false")
    boolean existsByPhone(@Param("phone") String phone);

    @Query(value = "SELECT DISTINCT u.id FROM users u " +
                   "WHERE u.deleted = false " +
                   "AND (" +
                   "  u.user_vector IS NULL OR " +
                   "  u.vector_updated_at IS NULL OR " +
                   "  EXISTS (SELECT 1 FROM user_likes l WHERE l.user_id = u.id AND l.update_at > u.vector_updated_at) OR " +
                   "  EXISTS (SELECT 1 FROM user_saves s WHERE s.user_id = u.id AND s.update_at > u.vector_updated_at) OR " +
                   "  EXISTS (SELECT 1 FROM reading_histories r WHERE r.user_id = u.id AND r.update_at > u.vector_updated_at)" +
                   ")", nativeQuery = true)
    List<UUID> findUserIdsWithPendingVectorUpdate();

    @Query("SELECT u FROM UserEntity u WHERE (LOWER(u.username) = LOWER(:lookup) OR LOWER(u.fullName) = LOWER(:lookup)) AND u.deleted = false")
    List<UserEntity> findByUsernameOrFullNameIgnoreCase(@Param("lookup") String lookup);
}
