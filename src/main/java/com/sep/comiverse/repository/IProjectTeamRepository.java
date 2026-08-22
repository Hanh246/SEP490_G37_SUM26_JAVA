package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ProjectTeamEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IProjectTeamRepository extends AbstractCrudRepository<ProjectTeamEntity, UUID> {
    @Override
    Optional<ProjectTeamEntity> findById(@Param("id") UUID uuid);

    @Query("select pt from ProjectTeamEntity pt join pt.members m where m.id = :userId")
    List<ProjectTeamEntity> findByMemberId(@Param("userId") UUID userId);

    Optional<ProjectTeamEntity> findByComicName(String comicName);

    @Query("SELECT pt FROM ProjectTeamEntity pt WHERE LOWER(pt.title) = LOWER(:title) AND pt.deleted = false")
    Optional<ProjectTeamEntity> findByTitleIgnoreCase(@Param("title") String title);

    @Query("SELECT pt FROM ProjectTeamEntity pt WHERE LOWER(pt.comicName) = LOWER(:comicName) AND pt.deleted = false")
    Optional<ProjectTeamEntity> findByComicNameIgnoreCase(@Param("comicName") String comicName);

    @Query("SELECT pt FROM ProjectTeamEntity pt WHERE LOWER(pt.comicName) = LOWER(:comicName) AND pt.deleted = false")
    List<ProjectTeamEntity> findAllByComicNameIgnoreCase(@Param("comicName") String comicName);

    org.springframework.data.domain.Page<ProjectTeamEntity> findByStatusAndDeletedFalse(String status, org.springframework.data.domain.Pageable pageable);

    @Query(value = "SELECT DISTINCT pt FROM ProjectTeamEntity pt LEFT JOIN pt.members m LEFT JOIN m.user u " +
                   "WHERE pt.deleted = false " +
                   "AND (u.id = :userId OR pt.leaderId = :userId OR (LOWER(pt.leaderName) = LOWER(:fullName) AND :fullName <> '') OR (LOWER(pt.leaderName) = LOWER(:username) AND :username <> '')) " +
                   "AND (LOWER(pt.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(pt.comicName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "ORDER BY pt.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT pt) FROM ProjectTeamEntity pt LEFT JOIN pt.members m LEFT JOIN m.user u " +
                        "WHERE pt.deleted = false " +
                        "AND (u.id = :userId OR pt.leaderId = :userId OR (LOWER(pt.leaderName) = LOWER(:fullName) AND :fullName <> '') OR (LOWER(pt.leaderName) = LOWER(:username) AND :username <> '')) " +
                        "AND (LOWER(pt.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(pt.comicName) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<ProjectTeamEntity> findMyTeamsPaginated(
            @Param("userId") UUID userId,
            @Param("fullName") String fullName,
            @Param("username") String username,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT DISTINCT pt FROM ProjectTeamEntity pt LEFT JOIN pt.members m LEFT JOIN m.user u " +
           "WHERE pt.deleted = false AND (u.id = :userId OR pt.leaderId = :userId OR (LOWER(pt.leaderName) = LOWER(:fullName) AND :fullName <> '') OR (LOWER(pt.leaderName) = LOWER(:username) AND :username <> '')) " +
           "ORDER BY pt.createdAt DESC")
    List<ProjectTeamEntity> findMyTeams(@Param("userId") UUID userId, @Param("fullName") String fullName, @Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(pt) > 0 THEN true ELSE false END FROM ProjectTeamEntity pt LEFT JOIN pt.members m WHERE pt.id = :teamId AND pt.deleted = false AND (m.id = :userId OR pt.leaderId = :userId)")
    boolean isUserMemberOfTeam(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(DISTINCT pt) FROM ProjectTeamEntity pt LEFT JOIN pt.members m WHERE pt.deleted = false AND LOWER(pt.status) IN ('active', 'ongoing') AND (m.user.id = :userId OR pt.leaderId = :userId)")
    long countActiveTeamsByUserId(@Param("userId") UUID userId);
}