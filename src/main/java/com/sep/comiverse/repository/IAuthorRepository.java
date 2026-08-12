package com.sep.comiverse.repository;

import com.sep.comiverse.entity.AuthorEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sep.comiverse.entity.enums.AuthorLicenseStatus;

@Repository
public interface IAuthorRepository extends AbstractCrudRepository<AuthorEntity, UUID> {

    @Query("SELECT a FROM AuthorEntity a JOIN FETCH a.user u WHERE u.id = :userId AND a.deleted = false")
    Optional<AuthorEntity> findByUserIdAndDeletedFalse(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AuthorEntity a WHERE a.user.id = :userId AND a.deleted = false")
    boolean existsByUserIdAndDeletedFalse(@Param("userId") UUID userId);

    @Query("""
            SELECT a FROM AuthorEntity a
            JOIN FETCH a.user u
            WHERE a.deleted = false
              AND a.licenseStatus IN :statuses
              AND a.licenseDeadlineAt IS NOT NULL
              AND a.licenseDeadlineAt < :now
            """)
    List<AuthorEntity> findExpiredLicenseCandidates(
            @Param("statuses") Collection<AuthorLicenseStatus> statuses,
            @Param("now") Instant now
    );

    @Query("""
            SELECT a FROM AuthorEntity a
            JOIN FETCH a.user u
            WHERE a.deleted = false
              AND (:status IS NULL OR a.licenseStatus = :status)
            ORDER BY a.updatedAt DESC
            """)
    List<AuthorEntity> findLicenseReviewItems(@Param("status") AuthorLicenseStatus status);
}
