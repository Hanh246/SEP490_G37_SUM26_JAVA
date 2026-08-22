package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.AuthorUploadTaskResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.AuthorUploadTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthorUploadTaskServiceTest {

    private AuthorUploadTaskService service;

    @BeforeEach
    void setUp() {
        service = new AuthorUploadTaskService();
    }

    @Test
    void createTaskStartsInQueuedState() {
        UUID authorId = UUID.randomUUID();

        AuthorUploadTaskResponse task = service.createTask(authorId, "CHAPTER_FOLDER", "Queued for upload");

        assertNotNull(task.getTaskId());
        assertEquals(authorId, task.getAuthorId());
        assertEquals("CHAPTER_FOLDER", task.getType());
        assertEquals(AuthorUploadTaskService.STATUS_QUEUED, task.getStatus());
        assertEquals(0, task.getProgress());
        assertEquals("Queued for upload", task.getMessage());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
    }

    @Test
    void createTaskRejectsMissingAuthor() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createTask(null, "CHAPTER_FOLDER", "Queued")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("Author id is required", error.getMessage());
    }

    @Test
    void createTaskLimitsOneAuthorToTwoConcurrentUploads() {
        UUID authorId = UUID.randomUUID();
        AuthorUploadTaskResponse first = service.createTask(authorId, "CHAPTER_FOLDER", "First");
        service.createTask(authorId, "CHAPTER_FOLDER", "Second");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createTask(authorId, "CHAPTER_FOLDER", "Third")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getHttpStatus());

        service.completeChapter(first.getTaskId(), ChapterPreviewResponse.builder().build());
        assertDoesNotThrow(() -> service.createTask(authorId, "CHAPTER_FOLDER", "Replacement"));
    }

    @Test
    void getTaskPreventsAnotherAuthorFromReadingUploadState() {
        UUID ownerId = UUID.randomUUID();
        UUID otherAuthorId = UUID.randomUUID();
        AuthorUploadTaskResponse task = service.createTask(ownerId, "CHAPTER_FOLDER", "Queued");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.getTask(task.getTaskId(), otherAuthorId)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getHttpStatus());
        assertEquals("Upload task does not belong to this author", error.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"-5, 0", "35, 35", "140, 100"})
    void markProcessingClampsProgressToValidRange(int requested, int expected) {
        UUID authorId = UUID.randomUUID();
        AuthorUploadTaskResponse task = service.createTask(authorId, "CHAPTER_FOLDER", "Queued");

        service.markProcessing(task.getTaskId(), "Uploading pages", requested);
        AuthorUploadTaskResponse updated = service.getTask(task.getTaskId(), authorId);

        assertEquals(AuthorUploadTaskService.STATUS_PROCESSING, updated.getStatus());
        assertEquals(expected, updated.getProgress());
        assertEquals("Uploading pages", updated.getMessage());
        assertNull(updated.getError());
    }

    @Test
    void completeChapterStoresPreviewAndFolderMessage() {
        UUID authorId = UUID.randomUUID();
        AuthorUploadTaskResponse task = service.createTask(authorId, "CHAPTER_FOLDER", "Queued");
        ChapterPreviewResponse preview = ChapterPreviewResponse.builder().build();

        service.completeChapter(task.getTaskId(), preview);
        AuthorUploadTaskResponse updated = service.getTask(task.getTaskId(), authorId);

        assertEquals(AuthorUploadTaskService.STATUS_COMPLETED, updated.getStatus());
        assertEquals(100, updated.getProgress());
        assertEquals("Chapter folder processed successfully", updated.getMessage());
        assertSame(preview, updated.getChapter());
        assertNull(updated.getError());
    }

    @Test
    void failRecordsSafeFallbackForMissingErrorDetails() {
        UUID authorId = UUID.randomUUID();
        AuthorUploadTaskResponse task = service.createTask(authorId, "CHAPTER_FOLDER", "Queued");

        service.fail(task.getTaskId(), null);
        AuthorUploadTaskResponse updated = service.getTask(task.getTaskId(), authorId);

        assertEquals(AuthorUploadTaskService.STATUS_FAILED, updated.getStatus());
        assertEquals(100, updated.getProgress());
        assertEquals("Unknown upload processing error", updated.getError());
    }

    @Test
    void getTaskReturnsNotFoundForUnknownTask() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.getTask(UUID.randomUUID(), UUID.randomUUID())
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    }
}
