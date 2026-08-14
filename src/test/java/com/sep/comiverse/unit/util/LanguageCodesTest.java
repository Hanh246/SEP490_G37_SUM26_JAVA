package com.sep.comiverse.unit.util;

import com.sep.comiverse.util.LanguageCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageCodesTest {

    @Test
    void normalizeMapsAliasesAndLocaleTags() {
        assertEquals("vi", LanguageCodes.normalize("Vietnamese"));
        assertEquals("vi", LanguageCodes.normalize("vi-VN"));
        assertEquals("vi", LanguageCodes.normalize("vie_VN"));
        assertEquals("en", LanguageCodes.normalize("en-US"));
        assertEquals("ja", LanguageCodes.normalize("jp"));
        assertEquals("vi", LanguageCodes.normalize(null));
    }
}
