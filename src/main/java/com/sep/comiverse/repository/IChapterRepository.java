package com.sep.comiverse.repository;

import com.sep.comiverse.dto.ChapterLiteDTO;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
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

    /**
     * Author ownership check.
     * Dùng cho author sửa/xóa chapter của chính mình.
     */
    Optional<ChapterEntity> findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(
            UUID id,
            UUID comicId,
            UUID authorId
    );

    /**
     * Public/readable chapter detail.
     * Dùng để chỉ lấy chapter đã PUBLISHED.
     */
    Optional<ChapterEntity> findByIdAndDeletedFalseAndModerationStatus(
            UUID id,
            ChapterStatus moderationStatus
    );

    /**
     * Check trùng chapter number trong cùng comic.
     */
    boolean existsByComic_IdAndChapterNumberAndDeletedFalse(
            UUID comicId,
            String chapterNumber
    );

    /**
     * Author quản lý danh sách chapter của comic thuộc mình.
     */
    Page<ChapterEntity> findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(
            UUID comicId,
            UUID authorId,
            Pageable pageable
    );

    /**
     * Internal/admin/author use.
     * Không dùng cho public reader nếu chưa lọc moderationStatus.
     */
    List<ChapterEntity> findAllByComic_IdAndDeletedFalse(UUID comicId);

    /**
     * Public list chapter theo comic.
     */
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

    /**
     * Public chapter metadata.
     * Chỉ trả chapter đã PUBLISHED.
     */
    @Query("""
        SELECT new com.sep.comiverse.dto.ChapterLiteDTO(
            c.id,
            c.comic.id,
            c.chapterNumber,
            c.title,
            c.viewCount,
            c.isPremium,
            c.createdAt
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

    /**
     * Public images.
     * Chỉ lấy ảnh của chapter đã PUBLISHED.
     *
     * Lưu ý: vì images là PostgreSQL text[] map sang List<String>,
     * nên có thể dùng method này, nhưng cách an toàn hơn vẫn là lấy ChapterEntity
     * bằng findByIdAndDeletedFalseAndModerationStatus rồi getImages().
     */
    @Query("""
        SELECT c.images
        FROM ChapterEntity c
        WHERE c.id = :chapterId
          AND c.deleted = false
          AND c.moderationStatus = :moderationStatus
        """)
    Optional<List<String>> findImagesByChapterIdAndStatus(
            @Param("chapterId") UUID chapterId,
            @Param("moderationStatus") ChapterStatus moderationStatus
    );
}