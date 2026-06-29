package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamJoinRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamJoinRequestRepository extends JpaRepository<TeamJoinRequestEntity, UUID> {
    List<TeamJoinRequestEntity> findByProjectTeamId(UUID projectTeamId);
}
