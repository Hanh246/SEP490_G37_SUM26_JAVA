package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {
    Optional<ChapterEntity> findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(UUID id, UUID comicId, UUID authorId);

    Optional<ChapterEntity> findByIdAndDeletedFalseAndModerationStatus(UUID id, ChapterStatus moderationStatus);

    boolean existsByComic_IdAndChapterNumberAndDeletedFalse(UUID comicId, String chapterNumber);

    Page<ChapterEntity> findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(UUID comicId, UUID authorId, Pageable pageable);

    List<ChapterEntity> findAllByComic_IdAndDeletedFalse(UUID comicId);

    Page<ChapterEntity> findAllByComic_IdAndDeletedFalseAndModerationStatus(UUID comicId, ChapterStatus moderationStatus, Pageable pageable);

    long countByComic_IdAndDeletedFalse(UUID comicId);

    long countByComic_IdAndModerationStatusAndDeletedFalse(UUID comicId, ChapterStatus moderationStatus);
}
