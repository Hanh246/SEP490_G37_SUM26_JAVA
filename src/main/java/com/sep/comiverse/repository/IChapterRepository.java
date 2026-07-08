package com.sep.comiverse.repository;

import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.ChapterEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {
    @Query("""
        SELECT new com.sep.comiverse.dto.ChapterLiteDTO
                (c.id, c.comic.id, c.chapterNumber, c.title, c.viewCount, c.isPremium, c.createdAt)
        FROM ChapterEntity c
        WHERE c.comic.id = :comicId
                AND c.deleted = false
        ORDER BY c.chapterNumber ASC
        """)
    List<ChapterLiteDTO> findChapterMetadataByComicId(@Param("comicId") UUID comicId);

    @Query("""
            SELECT c.images
            FROM ChapterEntity c
            WHERE c.id = :chapterId
                    AND c.deleted = false
            """)
    List<String> findImagesByChapterId(@Param("chapterId") UUID chapterId);
}
