package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class PageController {
    private final IPageTranslationRepository pageTranslationRepository;

    public PageController(IPageTranslationRepository pageTranslationRepository) {
        this.pageTranslationRepository = pageTranslationRepository;
    }

    @GetMapping("/translate-workspace/{taskId}")
    public ResponseEntity<?> getPagesForTask(@PathVariable UUID taskId) {
        List<PageTranslationEntity> pages = pageTranslationRepository.findByTaskId_IdOrderByPageNumberAsc(taskId);

        List<ChapterPageDTO> result = pages.stream()
                .map(this::toPageDetailDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private ChapterPageDTO toPageDetailDto(PageTranslationEntity page) {
        return ChapterPageDTO.builder()
                .pageId(page.getId())
                .pageNumber(page.getPageNumber())
                .imageUrl(page.getImageUrl())
                .status(page.getStatus())
                .build();
    }
}
