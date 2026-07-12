package com.sep.comiverse.controller;

import com.sep.comiverse.dto.ChapterPageDTO;
import com.sep.comiverse.entity.PageTranslationEntity;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IPageTranslationRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Lưu bubbles (vùng chọn + bản dịch + màu sắc + hình dạng) cho 1 trang cụ thể.
     * Dùng LUÔN ChapterPageDTO làm request body — chỉ đọc field "bubbles" trong đó,
     * các field khác (pageNumber, imageUrl, status...) nếu có gửi lên cũng bị BỎ QUA,
     * không ghi đè — tránh trường hợp frontend gửi thiếu/sai các field đó làm hỏng dữ liệu.
     *
     * ⚠️ ChapterPageDTO BẮT BUỘC phải có @NoArgsConstructor (và setter/​@Data) thì
     * Jackson mới deserialize được JSON gửi lên — khác với lúc trả response (GET) chỉ
     * cần getter/builder là đủ.
     */
    @PutMapping("/translate-workspace/pages/{pageId}/bubbles")
    public ResponseEntity<?> saveBubbles(@PathVariable UUID pageId, @RequestBody ChapterPageDTO request) {
        PageTranslationEntity page = pageTranslationRepository.findById(pageId)
                .orElse(null);

        if (page == null) {
            return ResponseEntity.notFound().build();
        }

        page.setBubbles(request.getBubbles() != null ? request.getBubbles() : "[]");
        pageTranslationRepository.save(page);

        return ResponseEntity.ok(toPageDetailDto(page));
    }

    private ChapterPageDTO toPageDetailDto(PageTranslationEntity page) {
        return ChapterPageDTO.builder()
                .pageId(page.getId())
                .pageNumber(page.getPageNumber())
                .imageUrl(page.getImageUrl())
                .status(page.getStatus())
                .bubbles(page.getBubbles())
                .build();
    }
}