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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicRepository extends AbstractCrudRepository<ComicEntity, UUID> {

    /**
     * ComicEntity only stores authorId, so the generic fuzzy-search implementation
     * cannot resolve a property named "author" directly from ComicEntity.
     *
     * The Comic mapper exposes "author" as a searchable key. Here that key is
     * translated to AuthorEntity.displayName. Both known author-id conventions
     * are supported because existing data may store either authors.id or users.id
     * in comics.author_id.
     */
    @Override
    default Specification<ComicEntity> contains(List<String> fields, String value) {
        return (root, query, criteriaBuilder) -> {
            String normalizedValue = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedValue + "%";
            List<Predicate> predicates = new ArrayList<>();

            if (fields != null && fields.contains("title")) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        pattern
                ));
            }

            if (fields != null && fields.contains("author")) {
                Subquery<UUID> authorSubquery = query.subquery(UUID.class);
                Root<ComicEntity> correlatedComic = authorSubquery.correlate(root);
                Root<AuthorEntity> author = authorSubquery.from(AuthorEntity.class);

                Predicate linkedByAuthorEntityId = criteriaBuilder.equal(
                        author.get("id"),
                        correlatedComic.get("authorId")
                );
                Predicate linkedByUserId = criteriaBuilder.equal(
                        author.get("user").get("id"),
                        correlatedComic.get("authorId")
                );

                authorSubquery.select(author.get("id"))
                        .where(
                                criteriaBuilder.isFalse(author.get("deleted")),
                                criteriaBuilder.or(linkedByAuthorEntityId, linkedByUserId),
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(author.get("displayName")),
                                        pattern
                                )
                        );

                predicates.add(criteriaBuilder.exists(authorSubquery));
            }

            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    Optional<ComicEntity> findByTitle(String title);

    Optional<ComicEntity> findByIdAndAuthorIdAndDeletedFalse(UUID id, UUID authorId);

    List<ComicEntity> findAllByDeletedFalseAndModerationStatus(ComicModerationStatus moderationStatus);

    Page<ComicEntity> findByDeletedFalseAndModerationStatusOrderByViewCountDesc(
            ComicModerationStatus moderationStatus,
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
                    OR LOWER(COALESCE(g.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR EXISTS (
                        SELECT a.id FROM AuthorEntity a
                        WHERE a.deleted = false
                          AND (a.id = c.authorId OR a.user.id = c.authorId)
                          AND LOWER(COALESCE(a.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    )
              )
            """)
    Page<ComicEntity> findPublishedComics(
            @Param("moderationStatus") ComicModerationStatus moderationStatus,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Fast path used when the author has not typed a search keyword (the common case
     * of simply opening "My comics"). Avoids the LEFT JOIN on genres, the DISTINCT
     * deduplication, and the correlated EXISTS subquery on AuthorEntity, all of which
     * are unnecessary work when there is nothing to filter by.
     */
    Page<ComicEntity> findByAuthorIdAndDeletedFalse(UUID authorId, Pageable pageable);

    /**
     * Full search path used only when the author actually provides a search keyword.
     * Kept identical to the previous behaviour (title / genre name / author display name).
     */
    @Query("""
            SELECT DISTINCT c FROM ComicEntity c
            LEFT JOIN c.genres g
            WHERE c.deleted = false
              AND c.authorId = :authorId
              AND (
                    LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(g.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR EXISTS (
                        SELECT a.id FROM AuthorEntity a
                        WHERE a.deleted = false
                          AND (a.id = c.authorId OR a.user.id = c.authorId)
                          AND LOWER(COALESCE(a.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    )
              )
            """)
    Page<ComicEntity> searchAuthorComics(
            @Param("authorId") UUID authorId,
            @Param("search") String search,
            Pageable pageable
    );

    List<ComicEntity> findAllByTitle(String title);

    List<ComicEntity> findAllByTitleIgnoreCase(String title);

    @Query("SELECT c FROM ComicEntity c WHERE c.summaryVector IS NULL AND c.deleted = false")
    List<ComicEntity> findComicsMissingVector();

    @Query(value = "SELECT * FROM comics " +
            "WHERE id != :currentComicId AND deleted = false AND moderation_status = 'PUBLISHED' " +
            "AND summary_vector IS NOT NULL " +
            "ORDER BY summary_vector <=> (SELECT summary_vector FROM comics WHERE id = :currentComicId) " +
            "LIMIT :limit", nativeQuery = true)
    List<ComicEntity> findSimilarComics(
            @Param("currentComicId") UUID currentComicId,
            @Param("limit") int limit
    );

    @Query(value = "SELECT c.* FROM comics c " +
            "WHERE c.deleted = false AND c.moderation_status = 'PUBLISHED' " +
            "AND c.summary_vector IS NOT NULL " +
            "ORDER BY c.summary_vector <=> (SELECT user_vector FROM users WHERE id = :userId) " +
            "LIMIT :limit", nativeQuery = true)
    List<ComicEntity> findRecommendedComicsForUser(
            @Param("userId") UUID userId,
            @Param("limit") int limit
    );

    @Query(value = "WITH recommended_comics AS (" +
            "    SELECT c.id, " +
            "           c.summary_vector <=> (SELECT user_vector FROM users WHERE id = :userId) AS distance " +
            "    FROM comics c " +
            "    WHERE c.deleted = false AND c.moderation_status = 'PUBLISHED' " +
            "      AND c.summary_vector IS NOT NULL" +
            ") " +
            "SELECT id FROM recommended_comics " +
            "WHERE (:cursorDistance IS NULL OR distance > :cursorDistance OR (distance = :cursorDistance AND id > :referenceId)) " +
            "ORDER BY distance ASC, id ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<UUID> findRecommendedComicIdsForUserCursor(
            @Param("userId") UUID userId,
            @Param("cursorDistance") Double cursorDistance,
            @Param("referenceId") UUID referenceId,
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
}
