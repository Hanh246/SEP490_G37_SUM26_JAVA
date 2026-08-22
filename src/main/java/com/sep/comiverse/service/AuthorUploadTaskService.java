package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.AuthorUploadTaskResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthorUploadTaskService {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private final Map<UUID, UploadTaskState> tasks = new ConcurrentHashMap<>();

    @Value("${author.upload.max-active-tasks-per-author:2}")
    private int maxActiveTasksPerAuthor = 2;

    public AuthorUploadTaskResponse createTask(UUID authorId, String type, String message) {
        if (authorId == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        synchronized (tasks) {
            long activeTasks = tasks.values().stream()
                    .filter(state -> authorId.equals(state.authorId))
                    .filter(state -> STATUS_QUEUED.equals(state.status) || STATUS_PROCESSING.equals(state.status))
                    .count();
            if (maxActiveTasksPerAuthor > 0 && activeTasks >= maxActiveTasksPerAuthor) {
                throw new CustomException(
                        429,
                        "Too many active chapter uploads. Finish one of the current uploads before starting another.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }

            UUID taskId = UUID.randomUUID();
            Instant now = Instant.now();
            UploadTaskState state = new UploadTaskState();
            state.taskId = taskId;
            state.authorId = authorId;
            state.type = type;
            state.status = STATUS_QUEUED;
            state.progress = 0;
            state.message = message;
            state.createdAt = now;
            state.updatedAt = now;
            tasks.put(taskId, state);
            return toResponse(state);
        }
    }

    public AuthorUploadTaskResponse getTask(UUID taskId, UUID authorId) {
        return toResponse(getTaskState(taskId, authorId));
    }

    public void markProcessing(UUID taskId, String message, int progress) {
        update(taskId, state -> {
            state.status = STATUS_PROCESSING;
            state.progress = clampProgress(progress);
            state.message = message;
            state.error = null;
        });
    }

    public void completeChapter(UUID taskId, ChapterPreviewResponse chapter) {
        update(taskId, state -> {
            state.status = STATUS_COMPLETED;
            state.progress = 100;
            state.message = "Chapter folder processed successfully";
            state.chapter = chapter;
            state.error = null;
        });
    }

    public void fail(UUID taskId, Throwable error) {
        update(taskId, state -> {
            state.status = STATUS_FAILED;
            state.progress = 100;
            state.message = "Upload processing failed";
            state.error = error == null || !StringUtils.hasText(error.getMessage())
                    ? "Unknown upload processing error"
                    : error.getMessage();
        });
    }

    private UploadTaskState getTaskState(UUID taskId, UUID authorId) {
        if (taskId == null) {
            throw new CustomException(400, "Upload task id is required", HttpStatus.BAD_REQUEST);
        }
        UploadTaskState state = tasks.get(taskId);
        if (state == null) {
            throw new CustomException(404, "Upload task not found or already expired", HttpStatus.NOT_FOUND);
        }
        if (authorId != null && !authorId.equals(state.authorId)) {
            throw new CustomException(403, "Upload task does not belong to this author", HttpStatus.FORBIDDEN);
        }
        return state;
    }

    private void update(UUID taskId, java.util.function.Consumer<UploadTaskState> updater) {
        UploadTaskState state = getTaskState(taskId, null);
        synchronized (state) {
            updater.accept(state);
            state.updatedAt = Instant.now();
        }
    }

    private AuthorUploadTaskResponse toResponse(UploadTaskState state) {
        return AuthorUploadTaskResponse.builder()
                .taskId(state.taskId)
                .authorId(state.authorId)
                .type(state.type)
                .status(state.status)
                .progress(state.progress)
                .message(state.message)
                .error(state.error)
                .chapter(state.chapter)
                .createdAt(Date.from(state.createdAt))
                .updatedAt(Date.from(state.updatedAt))
                .build();
    }

    private int clampProgress(int progress) {
        return Math.max(0, Math.min(100, progress));
    }

    private static class UploadTaskState {
        private UUID taskId;
        private UUID authorId;
        private String type;
        private String status;
        private Integer progress;
        private String message;
        private String error;
        private ChapterPreviewResponse chapter;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
