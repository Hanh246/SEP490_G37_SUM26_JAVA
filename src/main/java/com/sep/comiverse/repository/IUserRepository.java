package com.sep.comiverse.repository;

import com.sep.comiverse.entity.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IUserRepository extends AbstractCrudRepository<UserEntity, UUID> {
    @Query("SELECT u FROM UserEntity u WHERE u.username = :username AND u.deleted = false")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.deleted = false")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u WHERE (u.username = :username OR u.email = :email) AND u.deleted = false")
    Optional<UserEntity> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.username = :username AND u.deleted = false")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.email = :email AND u.deleted = false")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.phone = :phone AND u.deleted = false")
    boolean existsByPhone(@Param("phone") String phone);
}
