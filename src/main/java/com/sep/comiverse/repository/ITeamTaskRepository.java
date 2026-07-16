package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITeamTaskRepository extends JpaRepository<TeamTaskEntity, UUID> {
    List<TeamTaskEntity> findByProjectTeamId(UUID projectTeamId);
    @Query("SELECT t FROM TeamTaskEntity t LEFT JOIN FETCH t.chapter WHERE t.id = :id")
    Optional<TeamTaskEntity> findByIdWithChapter(@Param("id") UUID id);
}
