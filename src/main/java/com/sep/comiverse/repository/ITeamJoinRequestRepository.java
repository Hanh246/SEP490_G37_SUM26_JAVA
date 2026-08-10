package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamJoinRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamJoinRequestRepository extends JpaRepository<TeamJoinRequestEntity, UUID> {
    List<TeamJoinRequestEntity> findByProjectTeamId(UUID projectTeamId);
    boolean existsByNameAndProjectTeamId(String name, UUID projectTeamId);
    List<TeamJoinRequestEntity> findByName(String name);

    // ── New queries for upgraded join flow ──
    long countByRequesterIdAndStatus(UUID requesterId, String status);
    List<TeamJoinRequestEntity> findByRequesterIdAndStatus(UUID requesterId, String status);
    List<TeamJoinRequestEntity> findByRequesterId(UUID requesterId);
    boolean existsByRequesterIdAndProjectTeamIdAndStatus(UUID requesterId, UUID projectTeamId, String status);
}
