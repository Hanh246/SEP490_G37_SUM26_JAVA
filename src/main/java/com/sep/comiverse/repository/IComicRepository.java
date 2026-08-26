package com.sep.comiverse.repository;

import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicRepository
        extends AbstractCrudRepository<ComicEntity, UUID> {
    long countByUpdatedAtGreaterThanEqualAndModerationStatusAndDeletedFalse(java.time.Instant threshold, com.sep.comiverse.entity.enums.ComicModerationStatus status);

    List<ComicEntity> findByModerationStatusAndDeletedFalse(com.sep.comiverse.entity.enums.ComicModerationStatus status);

    @Query("SELECT c.publicationStatus, COUNT(c) FROM ComicEntity c WHERE c.deleted = false AND c.moderationStatus = 'PUBLISHED' GROUP BY c.publicationStatus")
    List<Object[]> countComicsByPublicationStatus();

    @Query("SELECT c.authorId, COUNT(c) FROM ComicEntity c WHERE c.deleted = false AND c.moderationStatus = 'PUBLISHED' GROUP BY c.authorId ORDER BY COUNT(c) DESC")
    List<Object[]> findTopAuthorsByPublishedComics(org.springframework.data.domain.Pageable pageable);

    @Override
    default Specification<ComicEntity> contains(
            List<String> fields,
            String value
    ) {
        return (root, query, criteriaBuilder) -> {
            String normalizedValue = value == null
                    ? ""
                    : value.trim().toLowerCase(Locale.ROOT);

            String pattern = "%" + normalizedValue + "%";
            List<Predicate> predicates = new ArrayList<>();

            if (fields != null && fields.contains("title")) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("title")),
                                pattern
                        )
                );
            }

            if (fields != null && fields.contains("author")) {
                Subquery<UUID> authorSubquery =
                        query.subquery(UUID.class);

                Root<ComicEntity> correlatedComic =
                        authorSubquery.correlate(root);

                Root<AuthorEntity> author =
                        authorSubquery.from(AuthorEntity.class);

                Predicate linkedByAuthorEntityId =
                        criteriaBuilder.equal(
                                author.get("id"),
                                correlatedComic.get("authorId")
                        );

                Predicate linkedByUserId =
                        criteriaBuilder.equal(
                                author.get("user").get("id"),
                                correlatedComic.get("authorId")
                        );

                authorSubquery
                        .select(author.get("id"))
                        .where(
                                criteriaBuilder.isFalse(
                                        author.get("deleted")
                                ),
                                criteriaBuilder.or(
                                        linkedByAuthorEntityId,
                                        linkedByUserId
                                ),
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                author.get("displayName")
                                        ),
                                        pattern
                                )
                        );

                predicates.add(
                        criteriaBuilder.exists(authorSubquery)
                );
            }

            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.or(
                    predicates.toArray(new Predicate[0])
            );
        };
    }

    // =========================================================
    // BASIC LOOKUP
    // =========================================================

    boolean existsByTitle(String title);

    Optional<ComicEntity> findByTitle(String title);

    Optional<ComicEntity> findByTitleIgnoreCase(String title);

    Optional<ComicEntity> findByIdAndAuthorIdAndDeletedFalse(
            UUID id,
            UUID authorId
    );

    Optional<ComicEntity>
    findByIdAndDeletedFalseAndModerationStatus(
            UUID id,
            ComicModerationStatus moderationStatus
    );

    @Query("""
            SELECT c
            FROM ComicEntity c
            LEFT JOIN FETCH c.genres
            WHERE c.id = :id
              AND c.deleted = false
            """)
    Optional<ComicEntity> findByIdWithGenres(@Param("id") UUID id);

    @Query("""
            SELECT c
            FROM ComicEntity c
            LEFT JOIN FETCH c.genres
            WHERE c.slug = :slug
              AND c.deleted = false
            """)
    Optional<ComicEntity> findBySlugWithGenres(@Param("slug") String slug);

    List<ComicEntity> findAllByTitle(String title);

    List<ComicEntity> findAllByTitleIgnoreCase(String title);

    // =========================================================
    // PUBLIC COMIC LIST
    // =========================================================

    List<ComicEntity>
    findAllByDeletedFalseAndModerationStatus(
            ComicModerationStatus moderationStatus
    );

    long countByModerationStatusAndDeletedFalse(
            ComicModerationStatus moderationStatus
    );

    @Query("""
            SELECT DISTINCT c
            FROM ComicEntity c
            LEFT JOIN FETCH c.genres
            WHERE c.deleted = false
              AND c.moderationStatus = :moderationStatus
            """)
    List<ComicEntity>
    findAllByDeletedFalseAndModerationStatusWithGenres(
            @Param("moderationStatus")
            ComicModerationStatus moderationStatus
    );

    @Query("""
            SELECT DISTINCT c
            FROM ComicEntity c
            LEFT JOIN FETCH c.genres
            WHERE c.deleted = false
            """)
    List<ComicEntity>
    findAllByDeletedFalseWithGenres();

    Page<ComicEntity>
    findByDeletedFalseAndModerationStatus(
            ComicModerationStatus moderationStatus,
            Pageable pageable
    );

    Page<ComicEntity>
    findByDeletedFalseAndModerationStatusOrderByViewCountDesc(
            ComicModerationStatus moderationStatus,
            Pageable pageable
    );

    Page<ComicEntity> findByOrderByViewCountDesc(
            Pageable pageable
    );

    @Query("""
            SELECT c
            FROM ComicEntity c
            WHERE c.deleted = false
              AND c.moderationStatus = :moderationStatus
            ORDER BY
                COALESCE(c.lastChapterUpdatedAt, c.createdAt) DESC,
                c.createdAt DESC
            """)
    Page<ComicEntity> findComicsByLatestChapters(
            @Param("moderationStatus")
            ComicModerationStatus moderationStatus,
            Pageable pageable
    );

    /*
     * Tìm comic public theo:
     * - title
     * - genre
     * - tên hiển thị của author
     */
    @Query("""
            SELECT DISTINCT c
            FROM ComicEntity c
            LEFT JOIN c.genres g
            WHERE c.deleted = false
              AND c.moderationStatus = :moderationStatus
              AND (
                    :search IS NULL
                    OR :search = ''
                    OR LOWER(c.title)
                        LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(g.name, ''))
                        LIKE LOWER(CONCAT('%', :search, '%'))
                    OR EXISTS (
                        SELECT a.id
                        FROM AuthorEntity a
                        WHERE a.deleted = false
                          AND (
                              a.id = c.authorId
                              OR a.user.id = c.authorId
                          )
                          AND LOWER(
                              COALESCE(a.displayName, '')
                          ) LIKE LOWER(
                              CONCAT('%', :search, '%')
                          )
                    )
              )
            """)
    Page<ComicEntity> findPublishedComics(
            @Param("moderationStatus")
            ComicModerationStatus moderationStatus,

            @Param("search")
            String search,

            Pageable pageable
    );

    // =========================================================
    // AUTHOR COMIC LIST
    // =========================================================

    /*
     * Fast path khi mở trang My Comics mà không nhập search.
     *
     * Không JOIN genres, không DISTINCT và không chạy subquery author.
     */
    Page<ComicEntity> findByAuthorIdAndDeletedFalse(
            UUID authorId,
            Pageable pageable
    );

    List<ComicEntity> findAllByAuthorIdAndDeletedFalseOrderByCreatedAtAsc(UUID authorId);

    long countByAuthorIdAndModerationStatusAndDeletedFalse(
            UUID authorId,
            ComicModerationStatus moderationStatus
    );

    long countByAuthorIdAndModerationStatusInAndDeletedFalse(
            UUID authorId,
            Collection<ComicModerationStatus> moderationStatuses
    );

    /*
     * Chỉ dùng khi search khác null và không rỗng.
     *
     * Giữ:
     * - title
     * - genre name
     * - author display name
     */
    @Query("""
            SELECT DISTINCT c
            FROM ComicEntity c
            LEFT JOIN c.genres g
            WHERE c.deleted = false
              AND c.authorId = :authorId
              AND (
                    LOWER(c.title)
                        LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(g.name, ''))
                        LIKE LOWER(CONCAT('%', :search, '%'))
                    OR EXISTS (
                        SELECT a.id
                        FROM AuthorEntity a
                        WHERE a.deleted = false
                          AND (
                              a.id = c.authorId
                              OR a.user.id = c.authorId
                          )
                          AND LOWER(
                              COALESCE(a.displayName, '')
                          ) LIKE LOWER(
                              CONCAT('%', :search, '%')
                          )
                    )
              )
            """)
    Page<ComicEntity> searchAuthorComics(
            @Param("authorId")
            UUID authorId,

            @Param("search")
            String search,

            Pageable pageable
    );

    // =========================================================
    // LATEST CHAPTER — NATIVE VERSION
    // =========================================================

    /*
     * Giữ method cũ vì có thể một service khác đang gọi overload này.
     *
     * Method phía trên:
     * findComicsByLatestChapters(status, pageable)
     *
     * Method này:
     * findComicsByLatestChapters(pageable)
     */
    @Query(
            value = """
                    SELECT c.*
                    FROM comics c
                    INNER JOIN chapters ch
                        ON c.id = ch.comic_id
                    GROUP BY c.id
                    ORDER BY MAX(ch.created_at) DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT c.id)
                    FROM comics c
                    INNER JOIN chapters ch
                        ON c.id = ch.comic_id
                    """,
            nativeQuery = true
    )
    Page<ComicEntity> findComicsByLatestChapters(
            Pageable pageable
    );

    // =========================================================
    // VECTOR
    // =========================================================

    /*
     * Dùng native query thay vì JPQL:
     *
     * c.summaryVector IS NULL
     *
     * vì summary_vector là PostgreSQL pgvector, Hibernate có thể xử lý
     * phép so sánh null không đúng tùy mapping.
     */
    @Query(
            value = """
                    SELECT c.*
                    FROM comics c
                    WHERE c.summary_vector IS NULL
                      AND c.deleted = false
                    """,
            nativeQuery = true
    )
    List<ComicEntity> findComicsMissingVector();

    @Query(
            value = """
                    SELECT *
                    FROM comics
                    WHERE id != :currentComicId
                      AND deleted = false
                      AND moderation_status = 'PUBLISHED'
                      AND summary_vector IS NOT NULL
                    ORDER BY summary_vector
                        <=> (
                            SELECT summary_vector
                            FROM comics
                            WHERE id = :currentComicId
                        )
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<ComicEntity> findSimilarComics(
            @Param("currentComicId")
            UUID currentComicId,

            @Param("limit")
            int limit
    );

    @Query(
            value = """
                    SELECT c.*
                    FROM comics c
                    WHERE c.deleted = false
                      AND c.moderation_status = 'PUBLISHED'
                      AND c.summary_vector IS NOT NULL
                    ORDER BY c.summary_vector
                        <=> (
                            SELECT user_vector
                            FROM users
                            WHERE id = :userId
                        )
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<ComicEntity> findRecommendedComicsForUser(
            @Param("userId")
            UUID userId,

            @Param("limit")
            int limit
    );

    @Query(
            value = """
        SELECT id
        FROM comics
        WHERE deleted = false
          AND moderation_status = 'PUBLISHED'
          AND summary_vector IS NOT NULL
          AND id NOT IN (:excludedIds)
          AND (
              :cursorDistance IS NULL
              OR summary_vector <=> CAST(:userVector AS vector) > :cursorDistance
              OR (
                  summary_vector <=> CAST(:userVector AS vector) = :cursorDistance
                  AND id > :referenceId
              )
          )
        ORDER BY
            summary_vector <=> CAST(:userVector AS vector) ASC,
            id ASC
        LIMIT :limit
        """,
            nativeQuery = true
    )
    List<UUID> findRecommendedComicIdsForUserCursor(
            @Param("userVector") float[] userVector,
            @Param("cursorDistance") Double cursorDistance,
            @Param("referenceId") UUID referenceId,
            @Param("excludedIds") List<UUID> excludedIds,
            @Param("limit") int limit
    );

    /*
     * Phiên bản mới lấy user_vector trực tiếp theo userId.
     *
     * Tạm giữ dưới dạng overload. Sau khi kiểm tra service đang sử dụng
     * phiên bản nào, có thể xóa phiên bản không còn dùng.
     */
    @Query(
            value = """
                    WITH recommended_comics AS (
                        SELECT
                            c.id,
                            c.summary_vector
                                <=> (
                                    SELECT u.user_vector
                                    FROM users u
                                    WHERE u.id = :userId
                                ) AS distance
                        FROM comics c
                        WHERE c.deleted = false
                          AND c.moderation_status = 'PUBLISHED'
                          AND c.summary_vector IS NOT NULL
                    )
                    SELECT id
                    FROM recommended_comics
                    WHERE (
                        :cursorDistance IS NULL
                        OR distance > :cursorDistance
                        OR (
                            distance = :cursorDistance
                            AND id > :referenceId
                        )
                    )
                    ORDER BY distance ASC, id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<UUID> findRecommendedComicIdsForUserCursor(
            @Param("userId")
            UUID userId,

            @Param("cursorDistance")
            Double cursorDistance,

            @Param("referenceId")
            UUID referenceId,

            @Param("limit")
            int limit
    );

    @Query(
            value = """
                    SELECT c.id
                    FROM comics c
                    WHERE c.deleted = false
                      AND c.moderation_status = 'PUBLISHED'
                      AND (
                          :cursorVal IS NULL
                          OR c.view_count < :cursorVal
                          OR (
                              c.view_count = :cursorVal
                              AND c.id < :referenceId
                          )
                      )
                    ORDER BY
                        c.view_count DESC,
                        c.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<UUID> findPopularComicIdsCursor(
            @Param("cursorVal")
            Long cursorVal,

            @Param("referenceId")
            UUID referenceId,

            @Param("limit")
            int limit
    );

    // =========================================================
    // GENRE
    // =========================================================

    @Query("""
            SELECT g.name
            FROM ComicEntity c
            JOIN c.genres g
            WHERE c.id = :comicId
              AND c.deleted = false
            """)
    List<String> findGenreNamesByComicId(
            @Param("comicId")
            UUID comicId
    );
}
