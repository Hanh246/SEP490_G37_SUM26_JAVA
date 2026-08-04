package com.sep.comiverse.repository;

import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.repository.projection.ComicChapterCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {

    @Query("""
        SELECT c.comic.id
        FROM ChapterEntity c
        WHERE c.id = :chapterId
          AND (c.deleted = false OR c.deleted IS NULL)
        """)
    Optional<UUID> findComicIdByChapterId(@Param("chapterId") UUID chapterId);

    Optional<ChapterEntity> findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
            UUID id,
            UUID comicId,
            UUID authorId
    );

    Optional<ChapterEntity> findByIdAndDeletedFalse(UUID id);

    Optional<ChapterEntity> findByIdAndDeletedFalseAndModerationStatus(
            UUID id,
            ChapterStatus moderationStatus
    );

    @Query("""
        SELECT c
        FROM ChapterEntity c
        JOIN FETCH c.comic comic
        WHERE c.id = :chapterId
          AND c.deleted = false
          AND c.moderationStatus = :moderationStatus
          AND comic.deleted = false
        """)
    Optional<ChapterEntity> findForOfflineDownload(
            @Param("chapterId") UUID chapterId,
            @Param("moderationStatus") ChapterStatus moderationStatus
    );

    boolean existsByComic_IdAndChapterNumberAndDeletedFalse(
            UUID comicId,
            String chapterNumber
    );

    Page<ChapterEntity> findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(
            UUID comicId,
            UUID authorId,
            Pageable pageable
    );

    List<ChapterEntity> findAllByComic_IdAndDeletedFalse(UUID comicId);

    List<ChapterEntity> findAllByComic_AuthorIdAndDeletedFalseOrderByCreatedAtAsc(UUID authorId);

    @Query("""
        SELECT c.comic.id AS comicId, COUNT(c.id) AS chapterCount
        FROM ChapterEntity c
        WHERE c.comic.id IN :comicIds
          AND c.deleted = false
        GROUP BY c.comic.id
        """)
    List<ComicChapterCountProjection> countActiveChaptersByComicIds(@Param("comicIds") List<UUID> comicIds);

    List<ChapterEntity> findAllByComic_IdAndDeletedFalseAndModerationStatus(
            UUID comicId,
            ChapterStatus moderationStatus
    );

    Page<ChapterEntity> findAllByComic_IdAndDeletedFalseAndModerationStatus(
            UUID comicId,
            ChapterStatus moderationStatus,
            Pageable pageable
    );

    long countByComic_IdAndDeletedFalse(UUID comicId);

    long countByComic_IdAndModerationStatusAndDeletedFalse(
            UUID comicId,
            ChapterStatus moderationStatus
    );

    @Query("""
        SELECT new com.sep.comiverse.dto.ChapterLiteDTO(
            c.id,
            c.comic.id,
            c.chapterNumber,
            c.title,
            c.viewCount,
            c.isPremium,
            c.createdAt,
            c.moderationStatus,
            c.approvedById,
            c.approvedAt
        )
        FROM ChapterEntity c
        WHERE c.comic.id = :comicId
          AND c.deleted = false
          AND c.moderationStatus = :moderationStatus
        ORDER BY c.chapterNumber ASC
        """)
    List<ChapterLiteDTO> findChapterMetadataByComicIdAndStatus(
            @Param("comicId") UUID comicId,
            @Param("moderationStatus") ChapterStatus moderationStatus
    );

    @Query("""
        SELECT new com.sep.comiverse.dto.ChapterLiteDTO(
            c.id,
            c.comic.id,
            c.chapterNumber,
            c.title,
            c.viewCount,
            c.isPremium,
            c.createdAt,
            c.moderationStatus,
            c.approvedById,
            c.approvedAt
        )
        FROM ChapterEntity c
        WHERE c.comic.id = :comicId
          AND c.deleted = false
          AND c.moderationStatus = :moderationStatus
        ORDER BY c.chapterNumber DESC
        """)
    List<ChapterLiteDTO> findChapterMetadataByComicId(
            @Param("comicId") UUID comicId,
            @Param("moderationStatus") ChapterStatus moderationStatus
    );

    @Query(value = """
        SELECT unnest(c.images)
        FROM chapters c
        WHERE c.id = :chapterId
          AND c.deleted = false
        """, nativeQuery = true)
    List<String> findImagesByChapterIdAndStatus(@Param("chapterId") UUID chapterId);


    @Query("""
        SELECT c.comic.id AS comicId, COUNT(c) AS chapterCount
        FROM ChapterEntity c
        WHERE c.comic.authorId = :authorId
          AND c.deleted = false
        GROUP BY c.comic.id
        """)
    List<ComicChapterCountProjection> countChaptersByComicForAuthor(@Param("authorId") UUID authorId);

    boolean existsByContentHashAndModerationStatus(String contentHash, com.sep.comiverse.entity.enums.ChapterStatus moderationStatus);
}
