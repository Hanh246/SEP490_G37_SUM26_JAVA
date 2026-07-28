package com.sep.comiverse.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Getter
@Service
public class ChapterPremiumPolicyService {

    private final int freeChapterCount;

    public ChapterPremiumPolicyService(
            @Value("${premium.free-chapter-count:2}") int freeChapterCount
    ) {
        this.freeChapterCount = Math.max(freeChapterCount, 0);
    }

    public boolean isPremiumChapter(String chapterNumber) {
        BigDecimal normalized = parseChapterNumber(chapterNumber);
        if (normalized == null) {
            // Invalid or legacy chapter numbers are not locked automatically.
            return false;
        }
        return normalized.compareTo(BigDecimal.valueOf(freeChapterCount)) > 0;
    }

    private BigDecimal parseChapterNumber(String chapterNumber) {
        if (chapterNumber == null || chapterNumber.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(chapterNumber.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
