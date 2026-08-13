package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.ForumCategoryRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ForumCategoryResponse;
import com.sep.comiverse.service.ForumCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/forum-categories")
public class ForumCategoryController {

    private final ForumCategoryService forumCategoryService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ForumCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(BaseResponse.<List<ForumCategoryResponse>>builder()
                .success(true)
                .data(forumCategoryService.getActiveCategories())
                .build());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    public ResponseEntity<BaseResponse<ForumCategoryResponse>> createCategory(
            @Valid @RequestBody ForumCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ForumCategoryResponse>builder()
                        .success(true)
                        .data(forumCategoryService.createCategory(request))
                        .message("Forum category created successfully")
                        .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    public ResponseEntity<BaseResponse<ForumCategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody ForumCategoryRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.<ForumCategoryResponse>builder()
                .success(true)
                .data(forumCategoryService.updateCategory(id, request))
                .message("Forum category updated successfully")
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(@PathVariable UUID id) {
        forumCategoryService.deleteCategory(id);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Forum category deleted successfully")
                .build());
    }
}
