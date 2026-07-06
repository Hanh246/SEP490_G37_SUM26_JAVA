package com.sep.comiverse.repository;

import com.sep.comiverse.entity.AuthorEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IAuthorRepository extends AbstractCrudRepository<AuthorEntity, UUID> {

    @Query("SELECT a FROM AuthorEntity a JOIN FETCH a.user u WHERE u.id = :userId AND a.deleted = false")
    Optional<AuthorEntity> findByUserIdAndDeletedFalse(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AuthorEntity a WHERE a.user.id = :userId AND a.deleted = false")
    boolean existsByUserIdAndDeletedFalse(@Param("userId") UUID userId);
}
