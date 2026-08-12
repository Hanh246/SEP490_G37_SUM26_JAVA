package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamJoinBanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITeamJoinBanRepository extends JpaRepository<TeamJoinBanEntity, UUID> {
    boolean existsByProjectTeamIdAndUserId(UUID projectTeamId, UUID userId);
    List<TeamJoinBanEntity> findByProjectTeamId(UUID projectTeamId);
    Optional<TeamJoinBanEntity> findByProjectTeamIdAndUserId(UUID projectTeamId, UUID userId);
}
