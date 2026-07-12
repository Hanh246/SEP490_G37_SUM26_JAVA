package com.sep.comiverse.repository;

import com.sep.comiverse.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc();
}
