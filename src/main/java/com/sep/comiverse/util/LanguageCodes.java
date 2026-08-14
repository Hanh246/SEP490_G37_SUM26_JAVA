package com.sep.comiverse.util;

import java.text.Normalizer;
import java.util.Locale;

public final class LanguageCodes {
    private LanguageCodes() {}

    public static String normalize(String raw) {
        return normalize(raw, "vi");
    }

    public static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback == null || fallback.isBlank() ? "vi" : fallback.trim().toLowerCase(Locale.ROOT);
        }
        String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceAll("\\s+", " ");

        String mapped = mapKnown(s);
        if (mapped != null) {
            return mapped;
        }

        int space = s.indexOf(' ');
        if (space > 0) {
            mapped = mapKnown(s.substring(0, space));
            if (mapped != null) {
                return mapped;
            }
            return s.substring(0, space);
        }
        return s;
    }

    private static String mapKnown(String s) {
        return switch (s) {
            case "vi", "vie", "vn", "vietnamese", "tieng viet" -> "vi";
            case "en", "eng", "english" -> "en";
            case "ja", "jpn", "jp", "japanese" -> "ja";
            case "ko", "kor", "kr", "korean" -> "ko";
            case "zh", "chi", "zho", "cn", "chinese" -> "zh";
            case "fr", "fra", "french" -> "fr";
            case "es", "spa", "spanish" -> "es";
            default -> null;
        };
    }
}
