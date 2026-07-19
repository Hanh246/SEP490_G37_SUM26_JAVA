package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ProjectTeamEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IProjectTeamRepository extends AbstractCrudRepository<ProjectTeamEntity, UUID> {
    @Override
    Optional<ProjectTeamEntity> findById(@Param("id") UUID uuid);

    @Query("select pt from ProjectTeamEntity pt join pt.members m where m.id = :userId")
    List<ProjectTeamEntity> findByMemberId(@Param("userId") UUID userId);

    Optional<ProjectTeamEntity> findByComicName(String comicName);

    @Query("SELECT pt FROM ProjectTeamEntity pt WHERE LOWER(pt.title) = LOWER(:title) AND pt.deleted = false")
    Optional<ProjectTeamEntity> findByTitleIgnoreCase(@Param("title") String title);

    @Query("SELECT pt FROM ProjectTeamEntity pt WHERE LOWER(pt.comicName) = LOWER(:comicName) AND pt.deleted = false")
    Optional<ProjectTeamEntity> findByComicNameIgnoreCase(@Param("comicName") String comicName);

    org.springframework.data.domain.Page<ProjectTeamEntity> findByStatusAndDeletedFalse(String status, org.springframework.data.domain.Pageable pageable);
}