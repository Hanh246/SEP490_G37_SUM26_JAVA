package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicMetricSnapshotRepository extends AbstractCrudRepository<ComicMetricSnapshotEntity, UUID> {
    Optional<ComicMetricSnapshotEntity> findTopByComicIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(UUID comicId, UUID authorId);
}
