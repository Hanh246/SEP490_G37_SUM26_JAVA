package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterCommentEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface IChapterCommentRepository extends AbstractCrudRepository<ChapterCommentEntity, UUID> {

    @Query("SELECT c FROM ChapterCommentEntity c WHERE c.chapterId = :chapterId " +
           "AND (:parentId IS NULL AND c.parentId IS NULL OR c.parentId = :parentId) " +
           "AND c.deleted = false")
    Page<ChapterCommentEntity> findByChapterIdAndParentId(
            @Param("chapterId") UUID chapterId,
            @Param("parentId") UUID parentId,
            Pageable pageable
    );
}

