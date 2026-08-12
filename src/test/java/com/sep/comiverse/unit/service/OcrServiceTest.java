package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.OcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock private RestTemplate restTemplate;
    private OcrService service;

    @BeforeEach
    void setUp() {
        service = new OcrService();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "visionModel", "gemini-test");
    }

    @Test
    void extractTextFromImage_parsesFirstGeminiTextAndTrimsIt() {
        Map<String, Object> body = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", "  Hello comic  "))
                        )
                ))
        );
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        String result = service.extractTextFromImage(new byte[]{1, 2, 3}, "image/png");

        assertEquals("Hello comic", result);
    }

    @Test
    void extractTextFromImage_emptyGeminiCandidates_returnsEmptyText() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("candidates", List.of())));

        assertEquals("", service.extractTextFromImage(new byte[]{1}, "image/jpeg"));
    }

    @Test
    void extractTextFromImage_wrapsTransportFailure() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.extractTextFromImage(new byte[]{1}, "image/jpeg")
        );

        assertTrue(error.getMessage().contains("Failed to extract text"));
        assertNotNull(error.getCause());
    }

    @Test
    void extractTextFromImageUrl_downloadsBytesAndDetectsMimeFromUrl() {
        when(restTemplate.getForObject("https://cdn.test/page.webp?x=1", byte[].class))
                .thenReturn(new byte[]{9, 8});
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "candidates", List.of(Map.of(
                                "content", Map.of("parts", List.of(Map.of("text", "OCR")))
                        ))
                )));

        assertEquals("OCR", service.extractTextFromImageUrl("https://cdn.test/page.webp?x=1"));
    }

    @Test
    void extractTextFromImageUrl_rejectsEmptyDownload() {
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(new byte[0]);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractTextFromImageUrl("https://cdn.test/page.jpg")
        );
        assertTrue(error.getMessage().contains("Failed to download"));
    }

    @Test
    void privateMimeDetection_coversSupportedExtensionsAndDefault() {
        assertEquals("image/png", ReflectionTestUtils.invokeMethod(service, "detectMimeType", "a.PNG"));
        assertEquals("image/webp", ReflectionTestUtils.invokeMethod(service, "detectMimeType", "a.webp"));
        assertEquals("image/gif", ReflectionTestUtils.invokeMethod(service, "detectMimeType", "a.gif"));
        assertEquals("image/jpeg", ReflectionTestUtils.invokeMethod(service, "detectMimeType", "a.jpeg"));
    }
}
