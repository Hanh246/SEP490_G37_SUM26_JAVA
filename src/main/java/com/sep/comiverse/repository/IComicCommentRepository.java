package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicCommentEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface IComicCommentRepository extends AbstractCrudRepository<ComicCommentEntity, UUID> {

    @Query("SELECT c FROM ComicCommentEntity c WHERE c.comicId = :comicId " +
           "AND (:parentId IS NULL AND c.parentId IS NULL OR c.parentId = :parentId) " +
           "AND c.deleted = false")
    Page<ComicCommentEntity> findByComicIdAndParentId(
            @Param("comicId") UUID comicId,
            @Param("parentId") UUID parentId,
            Pageable pageable
    );
}

