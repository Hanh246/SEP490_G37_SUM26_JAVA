package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterPageEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IChapterPageRepository extends AbstractCrudRepository<ChapterPageEntity, UUID> {
    List<ChapterPageEntity> findAllByChapterIdAndDeletedFalseOrderByPageNumberAsc(UUID chapterId);
}
