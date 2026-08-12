package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.ForumCategoryRequest;
import com.sep.comiverse.dto.response.ForumCategoryResponse;
import com.sep.comiverse.entity.ForumCategoryEntity;
import com.sep.comiverse.entity.ForumThreadEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IForumCategoryRepository;
import com.sep.comiverse.repository.IForumThreadRepository;
import com.sep.comiverse.service.ForumCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumCategoryServiceTest {

    @Mock
    private IForumCategoryRepository forumCategoryRepository;

    @Mock
    private IForumThreadRepository forumThreadRepository;

    private ForumCategoryService forumCategoryService;

    @BeforeEach
    void setUp() {
        forumCategoryService = new ForumCategoryService(
                forumCategoryRepository,
                forumThreadRepository
        );
    }

    @Test
    void createCategoryPersistsNormalizedNameAndColor() {
        ForumCategoryRequest request = request("  Fan   Art  ", "#A855F7");
        when(forumCategoryRepository.existsByNameIgnoreCaseAndDeletedFalse("Fan Art"))
                .thenReturn(false);
        when(forumCategoryRepository.save(any(ForumCategoryEntity.class)))
                .thenAnswer(invocation -> {
                    ForumCategoryEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        ForumCategoryResponse response = forumCategoryService.createCategory(request);

        assertEquals("Fan Art", response.getName());
        assertEquals("#a855f7", response.getColor());
    }

    @Test
    void updateCategoryRenamesExistingThreads() {
        UUID categoryId = UUID.randomUUID();
        ForumCategoryEntity category = category(categoryId, "Fan Art");
        ForumThreadEntity thread = ForumThreadEntity.builder().category("Fan Art").build();
        when(forumCategoryRepository.findByIdAndDeletedFalse(categoryId))
                .thenReturn(Optional.of(category));
        when(forumCategoryRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalse("Artwork", categoryId))
                .thenReturn(false);
        when(forumCategoryRepository.save(category)).thenReturn(category);
        when(forumThreadRepository.findByCategoryIgnoreCaseAndDeletedFalse("Fan Art"))
                .thenReturn(List.of(thread));

        forumCategoryService.updateCategory(categoryId, request("Artwork", "#3B82F6"));

        assertEquals("Artwork", thread.getCategory());
        verify(forumThreadRepository).saveAll(List.of(thread));
    }

    @Test
    void deleteCategoryMovesThreadsToGeneralAndSoftDeletesCategory() {
        UUID categoryId = UUID.randomUUID();
        ForumCategoryEntity category = category(categoryId, "Spoilers");
        ForumThreadEntity thread = ForumThreadEntity.builder().category("Spoilers").build();
        when(forumCategoryRepository.findByIdAndDeletedFalse(categoryId))
                .thenReturn(Optional.of(category));
        when(forumThreadRepository.findByCategoryIgnoreCaseAndDeletedFalse("Spoilers"))
                .thenReturn(List.of(thread));

        forumCategoryService.deleteCategory(categoryId);

        assertEquals("General", thread.getCategory());
        assertEquals(true, category.getDeleted());
        assertFalse(category.getIsActive());
        verify(forumThreadRepository).saveAll(List.of(thread));
        verify(forumCategoryRepository).save(category);
    }

    @Test
    void deleteCategoryRejectsGeneralCategory() {
        UUID categoryId = UUID.randomUUID();
        when(forumCategoryRepository.findByIdAndDeletedFalse(categoryId))
                .thenReturn(Optional.of(category(categoryId, "General")));

        assertThrows(CustomException.class, () -> forumCategoryService.deleteCategory(categoryId));

        verify(forumThreadRepository, never()).saveAll(any());
    }

    private ForumCategoryRequest request(String name, String color) {
        ForumCategoryRequest request = new ForumCategoryRequest();
        request.setName(name);
        request.setColor(color);
        return request;
    }

    private ForumCategoryEntity category(UUID id, String name) {
        ForumCategoryEntity category = ForumCategoryEntity.builder()
                .name(name)
                .color("#a855f7")
                .isActive(true)
                .build();
        category.setId(id);
        return category;
    }
}
