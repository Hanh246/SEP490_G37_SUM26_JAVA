package com.sep.comiverse.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.exception.OfflineDownloadException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Service
public class OfflineSourceImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final OfflineDownloadProperties properties;
    private final HttpClient httpClient;

    public OfflineSourceImageService(OfflineDownloadProperties properties) {
        this.properties = properties;
        Duration connectTimeout = properties.getConnectTimeout() == null
                ? Duration.ofSeconds(8)
                : properties.getConnectTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DownloadedPage download(String rawUrl) {
        URI uri = validateUri(rawUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getSourceRequestTimeout())
                    .header("Accept", "image/webp,image/png,image/jpeg")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() != 200) {
                closeQuietly(response.body());
                throw sourceUnavailable();
            }

            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength > properties.getMaxPageBytes()) {
                closeQuietly(response.body());
                throw chapterTooLarge();
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                closeQuietly(response.body());
                throw new OfflineDownloadException(
                        "SOURCE_CONTENT_INVALID",
                        "A chapter page did not contain a supported image",
                        HttpStatus.BAD_GATEWAY
                );
            }

            try (InputStream input = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > properties.getMaxPageBytes()) {
                        throw chapterTooLarge();
                    }
                    output.write(buffer, 0, read);
                }
                if (total == 0L) {
                    throw new OfflineDownloadException(
                            "SOURCE_CONTENT_INVALID",
                            "A chapter page was empty",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                byte[] bytes = output.toByteArray();
                if (!matchesMagic(bytes, contentType)) {
                    throw new OfflineDownloadException(
                            "SOURCE_CONTENT_INVALID",
                            "A chapter page image signature did not match its content type",
                            HttpStatus.BAD_GATEWAY
                    );
                }
                return new DownloadedPage(bytes, contentType);
            }
        } catch (OfflineDownloadException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw sourceUnavailable();
        } catch (Exception exception) {
            throw sourceUnavailable();
        }
    }

    private URI validateUri(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowedHost = properties.getAllowedImageHosts().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(rule -> matchesHost(host, rule));
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !allowedHost
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException("Source URL is not allowed");
            }
            return uri;
        } catch (Exception exception) {
            throw new OfflineDownloadException(
                    "SOURCE_URL_NOT_ALLOWED",
                    "A chapter page source is not allowed for protected downloads",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private boolean matchesHost(String host, String rule) {
        if (rule.startsWith("*.")) {
            String suffix = rule.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(rule);
    }

    private boolean matchesMagic(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';
            default -> false;
        };
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
            // The response has already failed; there is nothing else to recover here.
        }
    }

    private OfflineDownloadException sourceUnavailable() {
        return new OfflineDownloadException(
                "SOURCE_UNAVAILABLE",
                "A chapter page could not be retrieved securely",
                HttpStatus.BAD_GATEWAY
        );
    }

    private OfflineDownloadException chapterTooLarge() {
        return new OfflineDownloadException(
                "CHAPTER_TOO_LARGE",
                "The chapter is too large for an offline package",
                HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    public record DownloadedPage(byte[] bytes, String contentType) {
    }
}
