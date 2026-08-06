package com.sep.comiverse.util;

import java.util.Arrays;
import java.util.List;

public class ProfanityFilterUtil {

    private static final List<String> BAD_WORDS = Arrays.asList(
            "fuck", "shit", "bitch", "asshole", "cunt", "dick",
            "đù", "đm", "vcl", "vãi", "cặc", "lồn", "đĩ", "chó đẻ"
    );

    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        
        String lowerCaseText = text.toLowerCase();
        
        // Simple word matching. Depending on strictness, we might want to check with word boundaries.
        String[] words = lowerCaseText.split("\\s+");
        for (String word : words) {
            // Remove common punctuation to check the raw word
            String cleanWord = word.replaceAll("[^a-zA-Z0-9áàảãạăắằẳẵặâấầẩẫậđéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵ]", "");
            if (BAD_WORDS.contains(cleanWord)) {
                return true;
            }
        }
        
        // Also check for direct inclusion of certain phrases
        for (String badWord : BAD_WORDS) {
            if (badWord.contains(" ") && lowerCaseText.contains(badWord)) {
                return true;
            }
        }

        return false;
    }
}
