package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ChapterEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface IChapterRepository extends AbstractCrudRepository<ChapterEntity, UUID> {
}
