package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.CloudinaryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private final CloudinaryService cloudinaryService;

    public UploadController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<String>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadImage(file);
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .success(true)
                .data(url)
                .build());
    }

    @PostMapping(value = "/list-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<List<String>>> uploadListImage(
            @RequestParam("file") List<MultipartFile> file) {
        List<String> url = cloudinaryService.uploadListImages(file);
        return ResponseEntity.ok(BaseResponse.<List<String>>builder()
                .success(true)
                .data(url)
                .build());
    }
}
