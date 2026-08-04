package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITeamTaskRepository extends JpaRepository<TeamTaskEntity, UUID> {
    List<TeamTaskEntity> findByProjectTeamId(UUID projectTeamId);
    @Query("SELECT t FROM TeamTaskEntity t LEFT JOIN FETCH t.chapter WHERE t.id = :id")
    Optional<TeamTaskEntity> findByIdWithChapter(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM TeamTaskEntity t WHERE t.projectTeamId = :teamId AND t.status IN ('TODO', 'IN_PROGRESS', 'PENDING_REVIEW')")
    long countIncompleteTasksByTeam(@Param("teamId") UUID teamId);

    @Query("SELECT COUNT(t) FROM TeamTaskEntity t WHERE t.projectTeamId = :teamId AND t.assigneeId = :assigneeId AND t.status IN ('TODO', 'IN_PROGRESS', 'PENDING_REVIEW')")
    long countIncompleteTasksByTeamAndAssignee(@Param("teamId") UUID teamId, @Param("assigneeId") UUID assigneeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TeamTaskEntity t WHERE t.chapter.id = :chapterId")
    void hardDeleteAllByChapterId(@Param("chapterId") UUID chapterId);

    @Query("SELECT COUNT(t) FROM TeamTaskEntity t WHERE t.assigneeId = :assigneeId AND t.status IN ('TODO', 'IN_PROGRESS', 'PENDING_REVIEW')")
    long countActiveTasksByAssigneeId(@Param("assigneeId") UUID assigneeId);

    List<TeamTaskEntity> findByChapter_Id(UUID chapterId);
    @Query("""
            SELECT t FROM TeamTaskEntity t
            LEFT JOIN FETCH t.chapter
            WHERE t.assigneeId = :assigneeId
              AND t.completedAt >= :from
              AND t.completedAt < :to
              AND LOWER(COALESCE(t.status, '')) IN ('completed', 'complete', 'done')
            ORDER BY t.completedAt ASC
            """)
    List<TeamTaskEntity> findCompletedForAssigneeInPeriod(
            @Param("assigneeId") UUID assigneeId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

}
