package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorComicPackageUploadResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorUploadAsyncService {

    private final AuthorUploadTaskService uploadTaskService;
    private final AuthorComicPackageUploadService comicPackageUploadService;
    private final AuthorChapterService chapterService;

    @Async("authorUploadExecutor")
    public void processComicPackage(UUID taskId, AuthorComicCreateRequest request, MultipartFile comicZipFile) {
        uploadTaskService.markProcessing(taskId, "Creating comic and processing package ZIP", 10);
        try {
            AuthorComicPackageUploadResponse result = comicPackageUploadService.uploadComicPackage(request, comicZipFile);
            uploadTaskService.completeComicPackage(taskId, result);
        } catch (Exception error) {
            uploadTaskService.fail(taskId, error);
        }
    }

    @Async("authorUploadExecutor")
    public void processChapterZip(UUID taskId, UUID comicId, ChapterUploadRequest request, MultipartFile zipFile) {
        uploadTaskService.markProcessing(taskId, "Processing chapter CBZ and uploading pages", 10);
        try {
            ChapterPreviewResponse result = chapterService.uploadChapterZip(comicId, request, zipFile);
            uploadTaskService.completeChapter(taskId, result);
        } catch (Exception error) {
            uploadTaskService.fail(taskId, error);
        }
    }
}
