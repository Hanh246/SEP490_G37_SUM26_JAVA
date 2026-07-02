package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {
    Optional<ChapterEntity> findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(UUID id, UUID comicId, UUID authorId);

    boolean existsByComic_IdAndChapterNumberAndDeletedFalse(UUID comicId, String chapterNumber);

    Page<ChapterEntity> findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(UUID comicId, UUID authorId, Pageable pageable);

    long countByComic_IdAndDeletedFalse(UUID comicId);
}

