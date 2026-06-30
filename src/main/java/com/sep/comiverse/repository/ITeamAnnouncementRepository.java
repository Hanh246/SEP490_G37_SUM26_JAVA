package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamAnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamAnnouncementRepository extends JpaRepository<TeamAnnouncementEntity, UUID> {
    List<TeamAnnouncementEntity> findByProjectTeamId(UUID projectTeamId);
}
