package com.sep.comiverse.unit.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.sep.comiverse.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;
    private CloudinaryService service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryService(cloudinary);
    }

    @Test
    void uploadImage_validImage_returnsSecureUrl() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", new byte[]{1, 2, 3});
        when(uploader.upload(any(), anyMap()))
                .thenReturn(Map.of("secure_url", "https://cdn.example/cover.png"));

        String result = service.uploadImage(file);

        assertEquals("https://cdn.example/cover.png", result);
    }

    @Test
    void uploadImage_rejectsInvalidMimeExtensionAndOversize() {
        assertThrows(RuntimeException.class, () -> service.uploadImage(null));

        MockMultipartFile text = new MockMultipartFile(
                "file", "cover.png", "text/plain", new byte[]{1});
        assertEquals("Only image file formats are accepted.",
                assertThrows(RuntimeException.class, () -> service.uploadImage(text)).getMessage());

        MockMultipartFile badExtension = new MockMultipartFile(
                "file", "cover.bmp", "image/bmp", new byte[]{1});
        assertTrue(assertThrows(RuntimeException.class, () -> service.uploadImage(badExtension))
                .getMessage().contains(".bmp"));

        MockMultipartFile noExtension = new MockMultipartFile(
                "file", "cover", "image/png", new byte[]{1});
        assertTrue(assertThrows(RuntimeException.class, () -> service.uploadImage(noExtension))
                .getMessage().contains("Missing file extension"));

        MockMultipartFile tooLarge = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);
        assertTrue(assertThrows(RuntimeException.class, () -> service.uploadImage(tooLarge))
                .getMessage().contains("Maximum limit is 5MB"));
    }

    @Test
    void uploadListImages_rejectsEmptyAndWrapsFailingFileName() throws Exception {
        assertEquals("File list is empty",
                assertThrows(RuntimeException.class, () -> service.uploadListImages(List.of())).getMessage());

        when(cloudinary.uploader()).thenReturn(uploader);
        MockMultipartFile first = new MockMultipartFile(
                "file", "first.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile second = new MockMultipartFile(
                "file", "second.jpg", "image/jpeg", new byte[]{2});
        when(uploader.upload(any(), anyMap()))
                .thenReturn(Map.of("secure_url", "https://cdn.example/first.jpg"))
                .thenThrow(new IOException("cloud unavailable"));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.uploadListImages(List.of(first, second))
        );
        assertTrue(error.getMessage().contains("second.jpg"));
    }

    @Test
    void uploadFile_acceptsRawFileAndWrapsCloudinaryIoFailure() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "%PDF-1.7".getBytes());
        when(uploader.upload(any(), anyMap()))
                .thenReturn(Map.of("secure_url", "https://cdn.example/license.pdf"));
        assertEquals("https://cdn.example/license.pdf", service.uploadFile(pdf));

        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("down"));
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.uploadFile(pdf));
        assertTrue(error.getMessage().contains("Failed to upload file"));
    }
}
