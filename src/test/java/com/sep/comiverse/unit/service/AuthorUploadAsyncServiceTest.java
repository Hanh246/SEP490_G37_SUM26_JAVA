package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorUploadAsyncService;
import com.sep.comiverse.service.AuthorUploadTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorUploadAsyncServiceTest {

    @Mock
    private AuthorUploadTaskService uploadTaskService;
    @Mock
    private AuthorChapterService chapterService;
    @Mock
    private MultipartFile firstImage;

    private AuthorUploadAsyncService service;

    @BeforeEach
    void setUp() {
        service = new AuthorUploadAsyncService(uploadTaskService, chapterService);
    }

    @Test
    void processChapterFolderCompletesTaskAfterSuccessfulUpload() {
        UUID taskId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest();
        List<MultipartFile> files = List.of(firstImage);
        List<String> paths = List.of("Chapter 1/01.jpg");
        ChapterPreviewResponse result = ChapterPreviewResponse.builder().build();

        when(chapterService.uploadChapterFolder(comicId, request, files, paths)).thenReturn(result);

        service.processChapterFolder(taskId, comicId, request, files, paths);

        verify(uploadTaskService).markProcessing(taskId, "Validating chapter folder and uploading pages", 10);
        verify(uploadTaskService).completeChapter(taskId, result);
    }

    @Test
    void processChapterFolderMarksTaskFailedWhenUploadThrows() {
        UUID taskId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        ChapterUploadRequest request = new ChapterUploadRequest();
        List<MultipartFile> files = List.of(firstImage);
        List<String> paths = List.of("Chapter 1/01.jpg");
        RuntimeException failure = new RuntimeException("Cloud upload failed");

        when(chapterService.uploadChapterFolder(comicId, request, files, paths)).thenThrow(failure);

        service.processChapterFolder(taskId, comicId, request, files, paths);

        verify(uploadTaskService).fail(taskId, failure);
    }
}
