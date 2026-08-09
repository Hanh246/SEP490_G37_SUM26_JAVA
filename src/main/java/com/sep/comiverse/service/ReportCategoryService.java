package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.CreateReportCategoryRequest;
import com.sep.comiverse.dto.request.UpdateReportCategoryRequest;
import com.sep.comiverse.dto.response.ReportCategoryResponse;
import com.sep.comiverse.entity.ReportCategoryEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IReportCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCategoryService {

    private final IReportCategoryRepository reportCategoryRepository;

    @Transactional(readOnly = true)
    public List<ReportCategoryResponse> getActiveCategories() {
        return getActiveCategories(null, null);
    }

    @Transactional(readOnly = true)
    public List<ReportCategoryResponse> getActiveCategories(ReportTargetType targetType, ReportAssignedRole assignedRole) {
        List<ReportCategoryEntity> list = (assignedRole != null)
                ? reportCategoryRepository.findByAssignedRoleAndIsActiveTrueAndDeletedFalseOrderByNameAsc(assignedRole)
                : reportCategoryRepository.findByIsActiveTrueAndDeletedFalseOrderByNameAsc();

        if (targetType != null) {
            list = list.stream()
                    .filter(cat -> cat.supportsTargetType(targetType))
                    .collect(Collectors.toList());
        }

        return list.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReportCategoryResponse> getAllCategories(Boolean isActive, ReportAssignedRole assignedRole, ReportTargetType targetType) {
        Specification<ReportCategoryEntity> spec = (root, query, cb) -> {
            var predicate = cb.and(
                    cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted")))
            );
            if (isActive != null) {
                predicate = cb.and(predicate, cb.equal(root.get("isActive"), isActive));
            }
            if (assignedRole != null) {
                predicate = cb.and(predicate, cb.equal(root.get("assignedRole"), assignedRole));
            }
            query.orderBy(cb.asc(root.get("name")));
            return predicate;
        };

        List<ReportCategoryEntity> categories = reportCategoryRepository.findAll(spec);
        if (targetType != null) {
            categories = categories.stream()
                    .filter(cat -> cat.supportsTargetType(targetType))
                    .collect(Collectors.toList());
        }

        return categories.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportCategoryResponse getCategoryById(UUID id) {
        ReportCategoryEntity category = reportCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomException(404, "Report category not found", HttpStatus.NOT_FOUND));
        return toResponse(category);
    }

    @Transactional
    public ReportCategoryResponse createCategory(UserEntity currentUser, CreateReportCategoryRequest request) {
        String trimmedName = request.getName().trim();
        if (reportCategoryRepository.existsByNameIgnoreCaseAndDeletedFalse(trimmedName)) {
            throw new CustomException(400, "Report category name '" + trimmedName + "' already exists", HttpStatus.BAD_REQUEST);
        }

        List<ReportTargetType> targetTypes = request.getTargetTypes() != null
                ? request.getTargetTypes()
                : new ArrayList<>();

        ReportCategoryEntity entity = ReportCategoryEntity.builder()
                .name(trimmedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .assignedRole(request.getAssignedRole())
                .targetTypes(targetTypes)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdBy(currentUser)
                .build();

        ReportCategoryEntity saved = reportCategoryRepository.save(entity);
        log.info("Created report category: id={}, name={}, assignedRole={}, targetTypes={}",
                saved.getId(), saved.getName(), saved.getAssignedRole(), saved.getTargetTypes());
        return toResponse(saved);
    }

    @Transactional
    public ReportCategoryResponse updateCategory(UUID id, UpdateReportCategoryRequest request) {
        ReportCategoryEntity entity = reportCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomException(404, "Report category not found", HttpStatus.NOT_FOUND));

        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();
            if (reportCategoryRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalse(trimmedName, id)) {
                throw new CustomException(400, "Report category name '" + trimmedName + "' already exists", HttpStatus.BAD_REQUEST);
            }
            entity.setName(trimmedName);
        }

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription().trim());
        }

        if (request.getAssignedRole() != null) {
            entity.setAssignedRole(request.getAssignedRole());
        }

        if (request.getTargetTypes() != null) {
            entity.setTargetTypes(request.getTargetTypes());
        }

        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        ReportCategoryEntity saved = reportCategoryRepository.save(entity);
        log.info("Updated report category: id={}, name={}, targetTypes={}", saved.getId(), saved.getName(), saved.getTargetTypes());
        return toResponse(saved);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        ReportCategoryEntity entity = reportCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomException(404, "Report category not found", HttpStatus.NOT_FOUND));

        entity.setDeleted(true);
        reportCategoryRepository.save(entity);
        log.info("Soft-deleted report category: id={}", id);
    }

    public ReportCategoryResponse toResponse(ReportCategoryEntity entity) {
        if (entity == null) return null;
        return ReportCategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .assignedRole(entity.getAssignedRole())
                .targetTypes(entity.getTargetTypes() != null ? entity.getTargetTypes() : Collections.emptyList())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
