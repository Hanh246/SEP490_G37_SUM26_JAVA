package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TeamPostCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ITeamPostCommentRepository extends JpaRepository<TeamPostCommentEntity, UUID> {
    List<TeamPostCommentEntity> findByAnnouncementIdOrderByTimeAsc(UUID announcementId);
}
