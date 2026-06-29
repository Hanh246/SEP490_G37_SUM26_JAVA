package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicRepository extends AbstractCrudRepository<ComicEntity, UUID> {
    boolean existsByTitle(String title);

    Optional<ComicEntity> findByTitle(String title);

    Optional<ComicEntity> findByIdAndAuthorIdAndDeletedFalse(UUID id, UUID authorId);

    boolean existsByAuthorIdAndSlugAndDeletedFalse(UUID authorId, String slug);

    @Query("""
            SELECT c FROM ComicEntity c
            WHERE c.deleted = false
              AND c.authorId = :authorId
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(c.slug, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(c.genres, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(c.status, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<ComicEntity> findAuthorComics(
            @Param("authorId") UUID authorId,
            @Param("search") String search,
            Pageable pageable
    );
}
