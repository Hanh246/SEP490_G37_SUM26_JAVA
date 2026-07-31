package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorComicPackageUploadResponse;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.AuthorChapterService;
import com.sep.comiverse.service.AuthorComicPackageUploadService;
import com.sep.comiverse.service.AuthorComicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorComicPackageUploadServiceTest {

    @Mock
    private AuthorComicService authorComicService;
    @Mock
    private AuthorChapterService authorChapterService;

    private AuthorComicPackageUploadService service;

    @BeforeEach
    void setUp() {
        service = new AuthorComicPackageUploadService(authorComicService, authorChapterService);
        ReflectionTestUtils.setField(service, "maxChapters", 100);
        ReflectionTestUtils.setField(service, "maxChapterZipSizeBytes", 1024L);
    }

    @Test
    void uploadComicPackageDerivesTitleSortsChaptersAndDelegatesEachArchive() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        AuthorComicCreateRequest request = request(authorId);
        request.setTitle("   ");
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("Chapter 10.zip", new byte[]{10}),
                entry("Chapter 2.zip", new byte[]{2}),
                entry("Chapter 1,5.zip", new byte[]{1, 5})
        );
        AuthorComicResponse created = AuthorComicResponse.builder().id(comicId).authorId(authorId).build();
        AuthorComicResponse refreshed = AuthorComicResponse.builder().id(comicId).authorId(authorId).build();
        when(authorComicService.createComic(request)).thenReturn(created);
        when(authorComicService.getComic(comicId, authorId)).thenReturn(refreshed);
        when(authorChapterService.uploadChapterZip(eq(comicId), any(ChapterUploadRequest.class), any(MultipartFile.class)))
                .thenAnswer(invocation -> {
                    ChapterUploadRequest chapterRequest = invocation.getArgument(1);
                    return ChapterPreviewResponse.builder()
                            .comicId(comicId)
                            .authorId(authorId)
                            .chapterNumber(chapterRequest.getChapterNumber())
                            .title(chapterRequest.getTitle())
                            .build();
                });

        AuthorComicPackageUploadResponse response = service.uploadComicPackage(request, packageFile);

        ArgumentCaptor<ChapterUploadRequest> requestCaptor = ArgumentCaptor.forClass(ChapterUploadRequest.class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(authorChapterService, org.mockito.Mockito.times(3))
                .uploadChapterZip(eq(comicId), requestCaptor.capture(), fileCaptor.capture());
        assertEquals("Epic Saga", request.getTitle());
        assertEquals(List.of("1.5", "2", "10"), requestCaptor.getAllValues().stream()
                .map(ChapterUploadRequest::getChapterNumber)
                .toList());
        assertEquals(List.of("Chapter 1,5", "Chapter 2", "Chapter 10"), requestCaptor.getAllValues().stream()
                .map(ChapterUploadRequest::getTitle)
                .toList());
        assertEquals(List.of("Chapter 1,5.zip", "Chapter 2.zip", "Chapter 10.zip"), fileCaptor.getAllValues().stream()
                .map(MultipartFile::getOriginalFilename)
                .toList());
        assertEquals(3, response.getChapterCount());
        assertEquals(refreshed, response.getComic());
        assertEquals("Comic package uploaded. 3 chapter(s) were created as preview.", response.getMessage());
    }

    @Test
    void uploadComicPackageRejectsNestedChapterFolder() throws Exception {
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("wrapper/Chapter 1.zip", new byte[]{1})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertTrue(error.getMessage().contains("Do not wrap chapter ZIP files in another folder"));
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    @Test
    void uploadComicPackageRejectsInvalidChapterArchiveName() throws Exception {
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("Episode 1.zip", new byte[]{1})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals("Chapter archive name must be like 'Chapter 1.zip' or 'Chapter 1,5.zip'. Invalid file: Episode 1.zip", error.getMessage());
        verify(authorComicService, never()).createComic(any());
    }

    @Test
    void uploadComicPackageRejectsDuplicateChapterNumberIgnoringFilenameCase() throws Exception {
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("Chapter 1.zip", new byte[]{1}),
                entry("chapter 1.zip", new byte[]{2})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals("Duplicate chapter number in comic package: 1", error.getMessage());
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    @Test
    void uploadComicPackageRejectsUnsafeArchivePath() throws Exception {
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("../Chapter 1.zip", new byte[]{1})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("Unsafe archive entry path: ../Chapter 1.zip", error.getMessage());
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    @Test
    void uploadComicPackageEnforcesChapterCountLimitBeforeCreatingComic() throws Exception {
        ReflectionTestUtils.setField(service, "maxChapters", 1);
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("Chapter 1.zip", new byte[]{1}),
                entry("Chapter 2.zip", new byte[]{2})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals("Comic package exceeds maximum chapter count of 1", error.getMessage());
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    @Test
    void uploadComicPackageEnforcesPerChapterArchiveSize() throws Exception {
        ReflectionTestUtils.setField(service, "maxChapterZipSizeBytes", 3L);
        MockMultipartFile packageFile = outerZip(
                "Epic Saga.zip",
                entry("Chapter 1.zip", new byte[]{1, 2, 3, 4})
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), packageFile)
        );

        assertEquals("Chapter archive exceeds package limit: Chapter 1.zip", error.getMessage());
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    @Test
    void uploadComicPackageRequiresOuterZipFile() {
        MockMultipartFile wrongFile = new MockMultipartFile(
                "comicZipFile",
                "Epic Saga.rar",
                "application/octet-stream",
                new byte[]{1}
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.uploadComicPackage(request(UUID.randomUUID()), wrongFile)
        );

        assertEquals("Comic package must be an outer .zip file named like 'Comic Title.zip'", error.getMessage());
        verifyNoInteractions(authorComicService, authorChapterService);
    }

    private AuthorComicCreateRequest request(UUID authorId) {
        AuthorComicCreateRequest request = new AuthorComicCreateRequest();
        request.setAuthorId(authorId);
        request.setTitle("Epic Saga");
        request.setLanguage("English");
        request.setCover("cover.jpg");
        return request;
    }

    private MockMultipartFile outerZip(String originalFilename, ArchiveEntry... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile(
                "comicZipFile",
                originalFilename,
                "application/zip",
                bytes.toByteArray()
        );
    }

    private ArchiveEntry entry(String name, byte[] content) {
        return new ArchiveEntry(name, content);
    }

    private record ArchiveEntry(String name, byte[] content) {
    }
}
