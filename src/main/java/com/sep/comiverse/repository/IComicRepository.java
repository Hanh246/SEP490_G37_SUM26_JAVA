package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ComicEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface IComicRepository extends AbstractCrudRepository<ComicEntity, UUID> {
    boolean existsByTitle(String title);
    java.util.Optional<ComicEntity> findByTitle(String title);
}
