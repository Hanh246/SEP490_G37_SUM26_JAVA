package com.sep.comiverse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class OcrService {

    private static final String OCR_PROMPT =
            "Extract all visible text from this comic/manga page image. "
                    + "Include speech bubbles, narration boxes, sound effects, and any other readable text. "
                    + "Return ONLY the extracted text without explanation. Preserve the original language.";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.vision.model:gemini-2.0-flash}")
    private String visionModel;

    public String extractTextFromImageUrl(String imageUrl) {
        byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Failed to download image from URL");
        }
        return extractTextFromImage(imageBytes, detectMimeType(imageUrl));
    }

    public String extractTextFromImage(byte[] imageBytes, String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + visionModel + ":generateContent?key=" + apiKey;

        Map<String, Object> inlineData = Map.of(
                "mime_type", mimeType,
                "data", base64
        );
        Map<String, Object> textPart = Map.of("text", OCR_PROMPT);
        Map<String, Object> imagePart = Map.of("inline_data", inlineData);
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> payload = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return parseGeminiTextResponse(response.getBody());
        } catch (Exception e) {
            log.error("OCR failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to extract text from image", e);
        }
    }

    private String detectMimeType(String imageUrl) {
        String lowerUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".png")) {
            return "image/png";
        }
        if (lowerUrl.contains(".webp")) {
            return "image/webp";
        }
        if (lowerUrl.contains(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    @SuppressWarnings("unchecked")
    private String parseGeminiTextResponse(Map<String, Object> body) {
        if (body == null) {
            return "";
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            return "";
        }

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        Object text = parts.get(0).get("text");
        return text != null ? text.toString().trim() : "";
    }
}
