package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamTaskRepository extends JpaRepository<TeamTaskEntity, UUID> {
    List<TeamTaskEntity> findByProjectTeamId(UUID projectTeamId);
}
