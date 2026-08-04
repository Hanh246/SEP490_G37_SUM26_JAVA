package com.sep.comiverse.unit.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.service.OfflineSourceImageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineSourceImageServiceTest {

    @Test
    void rejectsNonHttpsAndNonAllowlistedSourcesBeforeConnecting() {
        OfflineDownloadProperties properties = new OfflineDownloadProperties();
        properties.setAllowedImageHosts(List.of("res.cloudinary.com"));
        OfflineSourceImageService service = new OfflineSourceImageService(properties);

        OfflineDownloadException http = assertThrows(
                OfflineDownloadException.class,
                () -> service.download("http://res.cloudinary.com/demo/page.png")
        );
        OfflineDownloadException foreignHost = assertThrows(
                OfflineDownloadException.class,
                () -> service.download("https://example.com/page.png")
        );

        assertEquals("SOURCE_URL_NOT_ALLOWED", http.getErrorCode());
        assertEquals("SOURCE_URL_NOT_ALLOWED", foreignHost.getErrorCode());
    }
}
