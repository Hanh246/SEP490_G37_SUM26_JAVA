package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {
    Optional<ChapterEntity> findByIdAndComicIdAndAuthorIdAndDeletedFalse(UUID id, UUID comicId, UUID authorId);

    boolean existsByComicIdAndChapterNumberAndDeletedFalse(UUID comicId, Integer chapterNumber);

    Page<ChapterEntity> findAllByComicIdAndAuthorIdAndDeletedFalse(UUID comicId, UUID authorId, Pageable pageable);
}
