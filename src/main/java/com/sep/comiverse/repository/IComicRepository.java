package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicRepository extends AbstractCrudRepository<ComicEntity, UUID> {
    boolean existsByTitle(String title);

    Optional<ComicEntity> findByTitle(String title);

    Optional<ComicEntity> findByIdAndAuthorIdAndDeletedFalse(UUID id, UUID authorId);

    Optional<ComicEntity> findByIdAndDeletedFalseAndModerationStatus(UUID id, ComicModerationStatus moderationStatus);

    boolean existsByAuthorIdAndSlugAndDeletedFalse(UUID authorId, String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    List<ComicEntity> findAllByDeletedFalseAndModerationStatus(ComicModerationStatus moderationStatus);

    @Query("SELECT DISTINCT c FROM ComicEntity c LEFT JOIN FETCH c.genres WHERE c.deleted = false AND c.moderationStatus = :moderationStatus")
    List<ComicEntity> findAllByDeletedFalseAndModerationStatusWithGenres(@Param("moderationStatus") ComicModerationStatus moderationStatus);

    Page<ComicEntity> findByDeletedFalseAndModerationStatus(ComicModerationStatus moderationStatus, Pageable pageable);

    Page<ComicEntity> findByDeletedFalseAndModerationStatusOrderByViewCountDesc(ComicModerationStatus moderationStatus, Pageable pageable);

    @Query("""
            SELECT c FROM ComicEntity c
            WHERE c.deleted = false
              AND c.moderationStatus = :moderationStatus
            ORDER BY COALESCE(c.lastChapterUpdatedAt, c.createdAt) DESC, c.createdAt DESC
            """)
    Page<ComicEntity> findComicsByLatestChapters(
            @Param("moderationStatus") ComicModerationStatus moderationStatus,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT c FROM ComicEntity c
            LEFT JOIN c.genres g
            WHERE c.deleted = false
              AND c.moderationStatus = :moderationStatus
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(c.slug, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(g.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<ComicEntity> findPublishedComics(
            @Param("moderationStatus") ComicModerationStatus moderationStatus,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT c FROM ComicEntity c
            LEFT JOIN c.genres g
            WHERE c.deleted = false
              AND c.authorId = :authorId
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(c.slug, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(g.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<ComicEntity> findAuthorComics(
            @Param("authorId") UUID authorId,
            @Param("search") String search,
            Pageable pageable
    );
    Optional<ComicEntity> findBySlug(String slug);

    Optional<ComicEntity> findByTitleIgnoreCase(String title);

    java.util.List<ComicEntity> findAllByTitle(String title);

    java.util.List<ComicEntity> findAllBySlug(String slug);

    java.util.List<ComicEntity> findAllByTitleIgnoreCase(String title);

    Page<ComicEntity> findByOrderByViewCountDesc(Pageable pageable);

    @Query(value = """
            SELECT c.* FROM comics c
            INNER JOIN chapters ch ON c.id = ch.comic_id
            GROUP BY c.id
            ORDER BY MAX(ch.created_at) DESC
            """,
            countQuery = """
                    SELECT COUNT(DISTINCT c.id) FROM comics c
                    INNER JOIN chapters ch ON c.id = ch.comic_id
                    """,
            nativeQuery = true)
    Page<ComicEntity> findComicsByLatestChapters(Pageable pageable);

    @Query("SELECT c FROM ComicEntity c WHERE c.summaryVector IS NULL AND c.deleted = false")
    List<ComicEntity> findComicsMissingVector();

    @Query(value = "SELECT * FROM comics " +
                   "WHERE id != :currentComicId AND deleted = false AND moderation_status = 'PUBLISHED' " +
                   "AND summary_vector IS NOT NULL " +
                   "ORDER BY summary_vector <=> (SELECT summary_vector FROM comics WHERE id = :currentComicId) " +
                   "LIMIT :limit", nativeQuery = true)
    List<ComicEntity> findSimilarComics(@Param("currentComicId") UUID currentComicId, @Param("limit") int limit);

    @Query(value = "SELECT c.* FROM comics c " +
                   "WHERE c.deleted = false AND c.moderation_status = 'PUBLISHED' " +
                   "AND c.summary_vector IS NOT NULL " +
                   "ORDER BY c.summary_vector <=> (SELECT user_vector FROM users WHERE id = :userId) " +
                   "LIMIT :limit", nativeQuery = true)
    List<ComicEntity> findRecommendedComicsForUser(@Param("userId") UUID userId, @Param("limit") int limit);

    @Query(value = "SELECT id " +
                   "FROM comics " +
                   "WHERE deleted = false " +
                   "  AND moderation_status = 'PUBLISHED' " +
                   "  AND summary_vector IS NOT NULL " +
                   "  AND (COALESCE(:excludedIds) IS NULL OR id NOT IN (:excludedIds)) " +
                   "  AND (:cursorDistance IS NULL OR (summary_vector <=> CAST(:userVector AS vector)) > :cursorDistance " +
                   "       OR ((summary_vector <=> CAST(:userVector AS vector)) = :cursorDistance AND id > :referenceId)) " +
                   "ORDER BY (summary_vector <=> CAST(:userVector AS vector)) ASC, id ASC " +
                   "LIMIT :limit", nativeQuery = true)
    List<UUID> findRecommendedComicIdsForUserCursor(
            @Param("userVector") float[] userVector,
            @Param("cursorDistance") Double cursorDistance,
            @Param("referenceId") UUID referenceId,
            @Param("excludedIds") List<UUID> excludedIds,
            @Param("limit") int limit
    );

    @Query(value = "SELECT c.id FROM comics c " +
                   "WHERE c.deleted = false AND c.moderation_status = 'PUBLISHED' " +
                   "AND (" +
                   "  :cursorVal IS NULL OR " +
                   "  c.view_count < :cursorVal OR " +
                   "  (c.view_count = :cursorVal AND c.id < :referenceId)" +
                   ") " +
                   "ORDER BY c.view_count DESC, c.id DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<UUID> findPopularComicIdsCursor(
            @Param("cursorVal") Long cursorVal,
            @Param("referenceId") UUID referenceId,
            @Param("limit") int limit
    );

    @Query("SELECT g.name FROM ComicEntity c JOIN c.genres g WHERE c.id = :comicId AND c.deleted = false")
    List<String> findGenreNamesByComicId(@Param("comicId") UUID comicId);
}
