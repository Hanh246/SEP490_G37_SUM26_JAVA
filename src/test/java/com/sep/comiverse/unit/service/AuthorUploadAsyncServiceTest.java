package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorComicPackageUploadResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorComicPackageUploadService;
import com.sep.comiverse.service.AuthorUploadAsyncService;
import com.sep.comiverse.service.AuthorUploadTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorUploadAsyncServiceTest {

    @Mock
    private AuthorUploadTaskService uploadTaskService;
    @Mock
    private AuthorComicPackageUploadService comicPackageUploadService;
    @Mock
    private AuthorChapterService chapterService;
    @Mock
    private MultipartFile zipFile;

    private AuthorUploadAsyncService service;

    @BeforeEach
    void setUp() {
        service = new AuthorUploadAsyncService(uploadTaskService, comicPackageUploadService, chapterService);
    }

    @Test
    void processComicPackageCompletesTaskAfterSuccessfulImport() {
        UUID taskId = UUID.randomUUID();
        AuthorComicCreateRequest request = new AuthorComicCreateRequest();
        AuthorComicPackageUploadResponse result = AuthorComicPackageUploadResponse.builder().build();
        when(comicPackageUploadService.uploadComicPackage(request, zipFile)).thenReturn(result);

        service.processComicPackage(taskId, request, zipFile);

        verify(uploadTaskService).markProcessing(taskId, "Creating comic and processing package ZIP", 10);
        verify(uploadTaskService).completeComicPackage(taskId, result);
    }

    @Test
    void processComicPackageMarksTaskFailedWhenImportThrows() {
        UUID taskId = UUID.randomUUID();
        AuthorComicCreateRequest request = new AuthorComicCreateRequest();
        RuntimeException failure = new RuntimeException("Invalid comic package");
        when(comicPackageUploadService.uploadComicPackage(request, zipFile)).thenThrow(failure);

        service.processComicPackage(taskId, request, zipFile);

        verify(uploadTaskService).fail(taskId, failure);
    }

    @Test
    void processChapterZipCompletesTaskAfterSuccessfulUpload() {
        UUID taskId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest();
        ChapterPreviewResponse result = ChapterPreviewResponse.builder().build();
        when(chapterService.uploadChapterZip(comicId, request, zipFile)).thenReturn(result);

        service.processChapterZip(taskId, comicId, request, zipFile);

        verify(uploadTaskService).markProcessing(taskId, "Processing chapter ZIP and uploading pages", 10);
        verify(uploadTaskService).completeChapter(taskId, result);
    }

    @Test
    void processChapterZipMarksTaskFailedWhenUploadThrows() {
        UUID taskId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest();
        RuntimeException failure = new RuntimeException("Cloud upload failed");
        when(chapterService.uploadChapterZip(comicId, request, zipFile)).thenThrow(failure);

        service.processChapterZip(taskId, comicId, request, zipFile);

        verify(uploadTaskService).fail(taskId, failure);
    }
}
