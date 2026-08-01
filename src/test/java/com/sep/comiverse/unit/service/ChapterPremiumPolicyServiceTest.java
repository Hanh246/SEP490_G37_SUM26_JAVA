package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.ChapterPremiumPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterPremiumPolicyServiceTest {

    @ParameterizedTest
    @CsvSource({
            "1, false",
            "2, false",
            "2.1, true",
            "'2,5', true",
            "3, true"
    })
    void locksOnlyChaptersAfterConfiguredFreeCount(String chapterNumber, boolean expectedPremium) {
        ChapterPremiumPolicyService service = new ChapterPremiumPolicyService(2);

        assertEquals(expectedPremium, service.isPremiumChapter(chapterNumber));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "prologue", "chapter-3"})
    void leavesInvalidAndLegacyChapterNumbersUnlocked(String chapterNumber) {
        ChapterPremiumPolicyService service = new ChapterPremiumPolicyService(2);

        assertFalse(service.isPremiumChapter(chapterNumber));
    }

    @Test
    void clampsNegativeConfigurationToZeroFreeChapters() {
        ChapterPremiumPolicyService service = new ChapterPremiumPolicyService(-5);

        assertEquals(0, service.getFreeChapterCount());
        assertFalse(service.isPremiumChapter("0"));
        assertTrue(service.isPremiumChapter("0.5"));
    }
}
