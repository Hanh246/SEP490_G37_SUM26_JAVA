package com.sep.comiverse.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sep.comiverse.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.chapter-folder:comiverse/chapters}")
    private String defaultFolder;

    public CloudinaryUploadResult uploadImage(byte[] bytes, String originalFileName, String targetFolder) {
        if (bytes == null || bytes.length == 0) {
            throw new CustomException(400, "Image bytes cannot be empty", HttpStatus.BAD_REQUEST);
        }

        String folder = StringUtils.hasText(targetFolder) ? targetFolder : defaultFolder;
        String publicId = buildPublicId(originalFileName);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", folder,
                            "public_id", publicId,
                            "resource_type", "image",
                            "overwrite", true
                    )
            );

            return CloudinaryUploadResult.builder()
                    .secureUrl(asString(result.get("secure_url")))
                    .publicId(asString(result.get("public_id")))
                    .width(asInteger(result.get("width")))
                    .height(asInteger(result.get("height")))
                    .bytes(asLong(result.get("bytes")))
                    .build();
        } catch (IOException e) {
            throw new CustomException(500, "Failed to upload image to Cloudinary", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildPublicId(String originalFileName) {
        String baseName = StringUtils.hasText(originalFileName) ? originalFileName : UUID.randomUUID().toString();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }

        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();

        if (!StringUtils.hasText(normalized)) {
            normalized = UUID.randomUUID().toString();
        }
        return normalized + "-" + UUID.randomUUID();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
