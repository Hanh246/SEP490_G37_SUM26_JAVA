package com.sep.comiverse.util;

import com.sep.comiverse.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public record BytesMultipartFile(
        String name,
        String originalFilename,
        String contentType,
        byte[] content
) implements MultipartFile {

    public static BytesMultipartFile from(MultipartFile file, String fallbackName) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "Archive file is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return new BytesMultipartFile(
                    file.getName() == null ? fallbackName : file.getName(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new CustomException(400, "Could not read uploaded archive file", HttpStatus.BAD_REQUEST);
        }
    }

    public static List<BytesMultipartFile> fromMany(List<MultipartFile> files, String fallbackName) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(400, "Chapter folder must contain image files", HttpStatus.BAD_REQUEST);
        }
        List<BytesMultipartFile> copies = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new CustomException(400, "Chapter folder contains an empty file", HttpStatus.BAD_REQUEST);
            }
            try {
                copies.add(new BytesMultipartFile(
                        file.getName() == null ? fallbackName : file.getName(),
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getBytes()
                ));
            } catch (IOException e) {
                throw new CustomException(400, "Could not read a file from the selected chapter folder", HttpStatus.BAD_REQUEST);
            }
        }
        return copies;
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
