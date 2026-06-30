package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IComicRepository extends AbstractCrudRepository<ComicEntity, UUID> {
    boolean existsByTitle(String title);

    Optional<ComicEntity> findByTitle(String title);

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
}
