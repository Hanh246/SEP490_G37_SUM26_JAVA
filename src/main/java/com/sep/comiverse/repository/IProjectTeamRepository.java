package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ProjectTeamEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface IProjectTeamRepository extends AbstractCrudRepository<ProjectTeamEntity, UUID> {
    java.util.Optional<ProjectTeamEntity> findByComicName(String comicName);
    org.springframework.data.domain.Page<ProjectTeamEntity> findByStatusAndDeletedFalse(String status, org.springframework.data.domain.Pageable pageable);
}
