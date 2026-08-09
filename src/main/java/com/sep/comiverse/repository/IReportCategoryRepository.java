package com.sep.comiverse.repository;

import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IReportCategoryRepository extends JpaRepository<ReportCategoryEntity, UUID>, JpaSpecificationExecutor<ReportCategoryEntity> {

    Optional<ReportCategoryEntity> findByIdAndDeletedFalse(UUID id);

    Optional<ReportCategoryEntity> findByIdAndIsActiveTrueAndDeletedFalse(UUID id);

    List<ReportCategoryEntity> findByIsActiveTrueAndDeletedFalseOrderByNameAsc();

    List<ReportCategoryEntity> findByAssignedRoleAndIsActiveTrueAndDeletedFalseOrderByNameAsc(ReportAssignedRole assignedRole);

    List<ReportCategoryEntity> findByDeletedFalseOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIdNotAndDeletedFalse(String name, UUID id);
}
