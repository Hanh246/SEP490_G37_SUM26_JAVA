package com.sep.comiverse.unit.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.CloudinaryStorageService;
import com.sep.comiverse.service.CloudinaryUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryStorageServiceTest {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;
    private CloudinaryStorageService service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryStorageService(cloudinary);
        ReflectionTestUtils.setField(service, "defaultFolder", "comiverse/chapters");
    }

    @Test
    void uploadImage_mapsCloudinaryMetadata() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "secure_url", "https://cdn.example/p1.jpg",
                "public_id", "chapters/p1",
                "width", 1200,
                "height", 1800,
                "bytes", 12345L
        ));

        CloudinaryUploadResult result = service.uploadImage(
                new byte[]{1, 2}, "Trang 01.JPG", null);

        assertEquals("https://cdn.example/p1.jpg", result.getSecureUrl());
        assertEquals("chapters/p1", result.getPublicId());
        assertEquals(1200, result.getWidth());
        assertEquals(1800, result.getHeight());
        assertEquals(12345L, result.getBytes());
    }

    @Test
    void uploadImage_rejectsEmptyBytesAndMapsIoFailure() throws Exception {
        CustomException empty = assertThrows(
                CustomException.class,
                () -> service.uploadImage(new byte[0], "page.jpg", null)
        );
        assertEquals(400, empty.getCode());

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("cloud down"));
        CustomException failed = assertThrows(
                CustomException.class,
                () -> service.uploadImage(new byte[]{1}, "page.jpg", null)
        );
        assertEquals(500, failed.getCode());
    }

    @Test
    void uploadRawFile_preservesExtensionAndMapsSparseMetadata() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "secure_url", "https://cdn.example/license.pdf",
                "public_id", "licenses/license-uuid.pdf"
        ));

        CloudinaryUploadResult result = service.uploadRawFile(
                "%PDF-".getBytes(), "Giấy phép.PDF", "licenses");

        assertEquals("https://cdn.example/license.pdf", result.getSecureUrl());
        assertNull(result.getWidth());
        assertNull(result.getHeight());
        assertNull(result.getBytes());
    }

    @Test
    void uploadRawFile_rejectsEmptyBytes() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadRawFile(null, "license.pdf", null)
        );
        assertEquals(400, error.getCode());
    }
}
