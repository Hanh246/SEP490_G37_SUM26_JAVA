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
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(r) FROM TeamJoinRequestEntity r WHERE r.requesterId = :requesterId AND LOWER(TRIM(COALESCE(r.status, 'pending'))) = LOWER(:status)")
    long countByRequesterIdAndStatus(@org.springframework.data.repository.query.Param("requesterId") UUID requesterId, @org.springframework.data.repository.query.Param("status") String status);
    @org.springframework.data.jpa.repository.Query("SELECT r FROM TeamJoinRequestEntity r WHERE r.requesterId = :requesterId AND LOWER(TRIM(COALESCE(r.status, 'pending'))) = LOWER(:status)")
    List<TeamJoinRequestEntity> findByRequesterIdAndStatus(@org.springframework.data.repository.query.Param("requesterId") UUID requesterId, @org.springframework.data.repository.query.Param("status") String status);
    
    List<TeamJoinRequestEntity> findByRequesterId(UUID requesterId);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(r) > 0 FROM TeamJoinRequestEntity r WHERE r.requesterId = :requesterId AND r.projectTeamId = :projectTeamId AND LOWER(TRIM(COALESCE(r.status, 'pending'))) = LOWER(:status)")
    boolean existsByRequesterIdAndProjectTeamIdAndStatus(@org.springframework.data.repository.query.Param("requesterId") UUID requesterId, @org.springframework.data.repository.query.Param("projectTeamId") UUID projectTeamId, @org.springframework.data.repository.query.Param("status") String status);
}
