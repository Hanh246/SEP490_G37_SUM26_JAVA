package com.sep.comiverse.repository;

import com.sep.comiverse.entity.RoleEntity;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IRoleRepository extends AbstractCrudRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByRoleName(String roleName);
}
