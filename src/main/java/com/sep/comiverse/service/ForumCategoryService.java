package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.ForumCategoryRequest;
import com.sep.comiverse.dto.response.ForumCategoryResponse;
import com.sep.comiverse.entity.ForumCategoryEntity;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumCategoryRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForumCategoryService {

    private static final String DEFAULT_COLOR = "#a855f7";
    private static final String FALLBACK_CATEGORY = "General";

    private final IForumCategoryRepository forumCategoryRepository;
    private final IForumThreadRepository forumThreadRepository;

    @Transactional(readOnly = true)
    public List<ForumCategoryResponse> getActiveCategories() {
        return forumCategoryRepository.findByIsActiveTrueAndDeletedFalseOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ForumCategoryResponse createCategory(ForumCategoryRequest request) {
        String name = normalizeName(request.getName());
        if (forumCategoryRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new CustomException(409, "Forum category already exists", HttpStatus.CONFLICT);
        }

        ForumCategoryEntity saved = forumCategoryRepository.save(ForumCategoryEntity.builder()
                .name(name)
                .color(normalizeColor(request.getColor()))
                .isActive(true)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public ForumCategoryResponse updateCategory(UUID id, ForumCategoryRequest request) {
        ForumCategoryEntity category = requireCategory(id);
        String oldName = category.getName();
        String newName = normalizeName(request.getName());
        if (forumCategoryRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalse(newName, id)) {
            throw new CustomException(409, "Forum category already exists", HttpStatus.CONFLICT);
        }

        category.setName(newName);
        category.setColor(normalizeColor(request.getColor()));
        ForumCategoryEntity saved = forumCategoryRepository.save(category);

        if (!oldName.equalsIgnoreCase(newName)) {
            List<ForumThreadEntity> threads = forumThreadRepository
                    .findByCategoryIgnoreCaseAndDeletedFalse(oldName);
            threads.forEach(thread -> thread.setCategory(newName));
            forumThreadRepository.saveAll(threads);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        ForumCategoryEntity category = requireCategory(id);
        if (FALLBACK_CATEGORY.equalsIgnoreCase(category.getName())) {
            throw new CustomException(400, "The General forum category cannot be deleted", HttpStatus.BAD_REQUEST);
        }

        List<ForumThreadEntity> threads = forumThreadRepository
                .findByCategoryIgnoreCaseAndDeletedFalse(category.getName());
        threads.forEach(thread -> thread.setCategory(FALLBACK_CATEGORY));
        forumThreadRepository.saveAll(threads);

        category.setDeleted(true);
        category.setIsActive(false);
        forumCategoryRepository.save(category);
    }

    private ForumCategoryEntity requireCategory(UUID id) {
        return forumCategoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomException(404, "Forum category not found", HttpStatus.NOT_FOUND));
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeColor(String value) {
        return value == null || value.isBlank() ? DEFAULT_COLOR : value.toLowerCase();
    }

    private ForumCategoryResponse toResponse(ForumCategoryEntity category) {
        return ForumCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .build();
    }
}
