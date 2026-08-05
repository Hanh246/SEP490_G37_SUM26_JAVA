package com.sep.comiverse.util;

import com.sep.comiverse.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public record BytesMultipartFile(
        String name,
        String originalFilename,
        String contentType,
        byte[] content
) implements MultipartFile {

    public static BytesMultipartFile from(MultipartFile file, String fallbackName) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "Uploaded file is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return new BytesMultipartFile(
                    file.getName() == null ? fallbackName : file.getName(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new CustomException(400, "Could not read uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
        return content == null ? 0L : content.length;
    }

    @Override
    public byte[] getBytes() {
        return content == null ? new byte[0] : content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(getBytes());
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), getBytes());
    }
}
