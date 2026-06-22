package com.sep.comiverse.repository;

import com.sep.comiverse.entity.GenreEntity;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IGenreRepository
        extends AbstractCrudRepository<GenreEntity, UUID> {
}
