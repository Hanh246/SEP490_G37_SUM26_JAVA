package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamMessageRepository extends JpaRepository<TeamMessageEntity, UUID> {
    List<TeamMessageEntity> findByProjectTeamId(UUID projectTeamId);
}
