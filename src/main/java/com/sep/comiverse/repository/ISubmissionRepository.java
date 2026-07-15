package com.sep.comiverse.repository;

import com.sep.comiverse.entity.SubmissionEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ISubmissionRepository extends AbstractCrudRepository<SubmissionEntity, UUID> {
    Optional<SubmissionEntity> findTopByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
            UUID comicId,
            UUID authorId,
            String queueType
    );

    Optional<SubmissionEntity> findTopByComicIdAndAuthorIdAndChapterIdIsNullAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
            UUID comicId,
            UUID authorId,
            String queueType
    );

    Optional<SubmissionEntity> findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
            UUID chapterId,
            UUID authorId,
            String queueType
    );

    boolean existsByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
            UUID chapterId,
            UUID authorId,
            String queueType,
            String status
    );

    List<SubmissionEntity> findAllByComicIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
            UUID comicId,
            UUID authorId,
            String queueType,
            String status
    );

    List<SubmissionEntity> findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
            UUID chapterId,
            UUID authorId,
            String queueType,
            String status
    );
}

