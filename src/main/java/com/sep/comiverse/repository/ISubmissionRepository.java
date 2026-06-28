package com.sep.comiverse.repository;

import com.sep.comiverse.entity.SubmissionEntity;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ISubmissionRepository extends AbstractCrudRepository<SubmissionEntity, UUID> {
}
