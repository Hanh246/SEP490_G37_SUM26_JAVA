package com.sep.comiverse.repository;

import com.sep.comiverse.entity.BannedKeywordEntity;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IBannedKeywordRepository extends AbstractCrudRepository<BannedKeywordEntity, UUID> {
}
