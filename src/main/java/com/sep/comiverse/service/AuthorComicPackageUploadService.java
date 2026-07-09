package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.AuthorComicCreateRequest;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.AuthorComicPackageUploadResponse;
import com.sep.comiverse.dto.response.AuthorComicResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class AuthorComicPackageUploadService {

    private static final Pattern CHAPTER_ZIP_NAME_PATTERN =
            Pattern.compile("(?i)^chapter\\s+([1-9][0-9]*(?:[,.][0-9]+)?)\\.cbz$");
    private static final Pattern NATURAL_PART_PATTERN = Pattern.compile("\\d+|\\D+");

    private final AuthorComicService authorComicService;
    private final AuthorChapterService authorChapterService;

    @Value("${author.comic-package.max-chapters:100}")
    private int maxChapters;

    @Value("${author.comic-package.max-chapter-zip-size-bytes:104857600}")
    private long maxChapterZipSizeBytes;

    @Transactional
    public AuthorComicPackageUploadResponse uploadComicPackage(AuthorComicCreateRequest request, MultipartFile comicZipFile) {
        validateRequest(request, comicZipFile);

        if (!StringUtils.hasText(request.getTitle())) {
            request.setTitle(resolveTitleFromZipFileName(comicZipFile.getOriginalFilename()));
        }

        List<ChapterZipCandidate> chapterZips = extractChapterZips(comicZipFile);
        AuthorComicResponse comic = authorComicService.createComic(request);
        UUID comicId = comic.getId();

        List<ChapterPreviewResponse> uploadedChapters = new ArrayList<>();
        for (ChapterZipCandidate chapterZip : chapterZips) {
            ChapterUploadRequest chapterRequest = new ChapterUploadRequest();
            chapterRequest.setAuthorId(request.getAuthorId());
            chapterRequest.setChapterNumber(chapterZip.chapterNumber());
            chapterRequest.setTitle("Chapter " + chapterZip.chapterNumber().replace(".", ","));

            uploadedChapters.add(authorChapterService.uploadChapterZip(
                    comicId,
                    chapterRequest,
                    new InMemoryMultipartFile("zipFile", chapterZip.fileName(), contentTypeForArchive(chapterZip.fileName()), chapterZip.bytes())
            ));
        }

        return AuthorComicPackageUploadResponse.builder()
                .comic(authorComicService.getComic(comicId, request.getAuthorId()))
                .chapters(uploadedChapters)
                .chapterCount(uploadedChapters.size())
                .message("Comic package uploaded. " + uploadedChapters.size() + " chapter(s) were created as preview.")
                .build();
    }

    private void validateRequest(AuthorComicCreateRequest request, MultipartFile comicZipFile) {
        if (request == null) {
            throw new CustomException(400, "Comic upload request is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getAuthorId() == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        if (comicZipFile == null || comicZipFile.isEmpty()) {
            throw new CustomException(400, "Comic package archive is required", HttpStatus.BAD_REQUEST);
        }
        String originalFilename = comicZipFile.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !isComicPackageZipFileName(originalFilename)) {
            throw new CustomException(400, "Comic package must be an outer .zip file named like 'Comic Title.zip'", HttpStatus.BAD_REQUEST);
        }
    }

    private List<ChapterZipCandidate> extractChapterZips(MultipartFile comicZipFile) {
        List<ChapterZipCandidate> chapterZips = new ArrayList<>();
        Set<String> chapterNumbers = new HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(comicZipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = normalizeEntryName(entry.getName());
                if (!isCandidateFile(entryName)) {
                    continue;
                }

                String[] pathParts = splitCleanPath(entryName);
                if (pathParts.length != 1) {
                    throw new CustomException(
                            400,
                            "Comic package must be exactly: Comic Title.zip -> Chapter 1.cbz -> 01.jpg. Do not wrap chapter CBZ files in another folder. Invalid entry: " + entryName,
                            HttpStatus.BAD_REQUEST
                    );
                }

                String fileName = pathParts[0];
                if (!isCbzArchiveFileName(fileName)) {
                    throw new CustomException(400, "Comic package can contain chapter .cbz files only. Invalid entry: " + entryName, HttpStatus.BAD_REQUEST);
                }

                String chapterNumber = parseChapterNumber(fileName);

                if (chapterNumber == null) {
                    throw new CustomException(
                            400,
                            "Chapter archive name must be like 'Chapter 1.cbz' or 'Chapter 1,5.cbz'. Invalid file: " + fileName,
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (!chapterNumbers.add(chapterNumber)) {
                    throw new CustomException(409, "Duplicate chapter number in comic package: " + chapterNumber, HttpStatus.CONFLICT);
                }

                byte[] bytes = readCurrentEntry(zipInputStream, fileName);
                if (bytes.length == 0) {
                    throw new CustomException(400, "Empty chapter archive in comic package: " + fileName, HttpStatus.BAD_REQUEST);
                }
                if (bytes.length > maxChapterZipSizeBytes) {
                    throw new CustomException(400, "Chapter archive exceeds package limit: " + fileName, HttpStatus.BAD_REQUEST);
                }

                chapterZips.add(new ChapterZipCandidate(fileName, chapterNumber, toSortNumber(chapterNumber), bytes));
                if (chapterZips.size() > maxChapters) {
                    throw new CustomException(400, "Comic package exceeds maximum chapter count of " + maxChapters, HttpStatus.BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            throw new CustomException(400, "Invalid or unreadable comic package ZIP", HttpStatus.BAD_REQUEST);
        }

        if (chapterZips.isEmpty()) {
            throw new CustomException(400, "Comic package does not contain any chapter .cbz files like 'Chapter 1.cbz' at root", HttpStatus.BAD_REQUEST);
        }

        chapterZips.sort(Comparator
                .comparing(ChapterZipCandidate::sortNumber)
                .thenComparing(ChapterZipCandidate::fileName, naturalComparator()));
        return chapterZips;
    }

    private byte[] readCurrentEntry(ZipInputStream zipInputStream, String fileName) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0L;
        while ((read = zipInputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxChapterZipSizeBytes) {
                throw new CustomException(400, "Chapter archive exceeds package limit: " + fileName, HttpStatus.BAD_REQUEST);
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private boolean isCandidateFile(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return false;
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/") || normalized.contains("/.__") || normalized.contains("/.")) {
            return false;
        }
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("..\\")) {
            throw new CustomException(400, "Unsafe archive entry path: " + entryName, HttpStatus.BAD_REQUEST);
        }
        return true;
    }

    private String normalizeEntryName(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/');
    }

    private String getBaseName(String entryName) {
        int slashIndex = entryName.lastIndexOf('/');
        return slashIndex >= 0 ? entryName.substring(slashIndex + 1) : entryName;
    }

    private String[] splitCleanPath(String entryName) {
        String normalized = normalizeEntryName(entryName);
        String[] rawParts = normalized.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : rawParts) {
            if (StringUtils.hasText(part)) {
                parts.add(part);
            }
        }
        return parts.toArray(new String[0]);
    }


    private boolean isComicPackageZipFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private boolean isCbzArchiveFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".cbz");
    }

    private String contentTypeForArchive(String fileName) {
        return "application/vnd.comicbook+zip";
    }

    private String parseChapterNumber(String fileName) {
        Matcher matcher = CHAPTER_ZIP_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).replace(',', '.');
    }

    private BigDecimal toSortNumber(String chapterNumber) {
        return new BigDecimal(chapterNumber.replace(',', '.'));
    }

    private String resolveTitleFromZipFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "Untitled Comic";
        }
        String fileName = getBaseName(normalizeEntryName(originalFilename));
        return fileName.replaceAll("(?i)\\.(zip|cbz)$", "").trim();
    }

    private Comparator<String> naturalComparator() {
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> compareNaturally(left, right, collator);
    }

    private int compareNaturally(String left, String right, Collator collator) {
        List<String> leftParts = splitNumberParts(left);
        List<String> rightParts = splitNumberParts(right);
        int limit = Math.min(leftParts.size(), rightParts.size());

        for (int index = 0; index < limit; index++) {
            String leftPart = leftParts.get(index);
            String rightPart = rightParts.get(index);
            boolean leftNumber = leftPart.chars().allMatch(Character::isDigit);
            boolean rightNumber = rightPart.chars().allMatch(Character::isDigit);
            int comparison = leftNumber && rightNumber
                    ? Integer.compare(Integer.parseInt(leftPart), Integer.parseInt(rightPart))
                    : collator.compare(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftParts.size(), rightParts.size());
    }

    private List<String> splitNumberParts(String value) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = NATURAL_PART_PATTERN.matcher(value);
        while (matcher.find()) {
            parts.add(matcher.group());
        }
        return parts;
    }

    private record ChapterZipCandidate(String fileName, String chapterNumber, BigDecimal sortNumber, byte[] bytes) {
    }

    private record InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) implements MultipartFile {
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
}
