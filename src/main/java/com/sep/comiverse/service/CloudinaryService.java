package com.sep.comiverse.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(MultipartFile file) {
        validateImage(file);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "tutor-avatar",
                            "resource_type", "image"
                    )
            );
            return result.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    public List<String> uploadListImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("File list is empty");
        }

        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String url = uploadImage(file);
                urls.add(url);
            } catch (Exception e) {
                throw new RuntimeException("Error occurred while uploading file: " + file.getOriginalFilename(), e);
            }
        }

        return urls;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload.");
        }

        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new RuntimeException("Only image file formats are accepted.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.contains(".")) {
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            List<String> allowedExtensions = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

            if (!allowedExtensions.contains(extension)) {
                throw new RuntimeException("File extension ." + extension + " is not supported. Allowed formats: jpg, jpeg, png, gif, webp.");
            }
        } else if (fileName != null) {
            throw new RuntimeException("Invalid file name format. Missing file extension.");
        }

        // Max size: 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("Image size is too large! Maximum limit is 5MB.");
        }
    }

    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload.");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("File size is too large! Maximum limit is 5MB.");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "comiverse-files",
                            "resource_type", "raw"
                    )
            );
            return result.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Cloudinary", e);
        }
    }
}