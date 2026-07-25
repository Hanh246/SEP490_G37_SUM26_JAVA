package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReviewCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IReviewCommentRepository extends JpaRepository<ReviewCommentEntity, UUID> {
    List<ReviewCommentEntity> findByPage_IdOrderByCreatedAtAsc(UUID pageId);
    Optional<ReviewCommentEntity> findByPage_IdAndBubbleIdAndAuthor_Id(UUID pageId, String bubbleId, UUID authorId);
    Optional<ReviewCommentEntity> findByPage_IdAndBubbleIdIsNullAndAuthor_Id(UUID pageId, UUID authorId);
}