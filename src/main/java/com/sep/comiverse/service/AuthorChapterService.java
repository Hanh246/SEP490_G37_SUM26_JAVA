package com.sep.comiverse.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.ChapterPageResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class AuthorChapterService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern NATURAL_PART_PATTERN = Pattern.compile("\\d+|\\D+");
    private static final Pattern CHAPTER_ZIP_NAME_PATTERN =
            Pattern.compile("(?i)^chapter\\s+([1-9][0-9]*(?:[,.][0-9]+)?)\\.cbz$");
    private static final Pattern CHAPTER_NUMBER_PATTERN =
            Pattern.compile("^[1-9][0-9]*(?:[,.][0-9]+)?$");

    private final AuthorComicService authorComicService;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final ISubmissionRepository submissionRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Value("${author.chapter.max-pages:200}")
    private int maxPages;

    @Transactional
    public ChapterPreviewResponse uploadChapterZip(UUID comicId, ChapterUploadRequest request, MultipartFile zipFile) {
        validateUploadRequest(request);
        validateZipFile(zipFile);

        ComicEntity comic = authorComicService.getOwnedComic(comicId, request.getAuthorId());
        String chapterNumber = resolveChapterNumber(request, zipFile);
        if (chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, chapterNumber)) {
            throw new CustomException(409, "Chapter number already exists for this comic", HttpStatus.CONFLICT);
        }

        List<ImageCandidate> images = extractAndValidateImages(zipFile);
        images.sort(imageNaturalComparator());

        List<String> imageUrls = new ArrayList<>();
        String targetFolder = "comiverse/chapters/" + comicId + "/chapter-" + chapterNumber;
        for (int index = 0; index < images.size(); index++) {
            ImageCandidate image = images.get(index);
            CloudinaryUploadResult upload = cloudinaryStorageService.uploadImage(
                    image.bytes(),
                    buildOrderedFileName(index + 1, image.originalFileName()),
                    targetFolder
            );
            imageUrls.add(upload.getSecureUrl());
        }

        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber(chapterNumber)
                .title(trimToNull(request.getTitle()))
                .moderationStatus(ChapterStatus.PREVIEW_READY)
                .images(imageUrls)
                .build();
        ChapterEntity savedChapter = chapterRepository.save(chapter);
        refreshComicChapterMetadata(comic);

        return toPreviewResponse(savedChapter);
    }

    @Transactional(readOnly = true)
    public ChapterPreviewResponse previewChapter(UUID comicId, UUID chapterId, UUID authorId) {
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);
        return toPreviewResponse(chapter);
    }

    @Transactional(readOnly = true)
    public Page<ChapterPreviewResponse> listChapters(UUID comicId, UUID authorId, PaginationSearchDTO pagination) {
        authorComicService.getOwnedComic(comicId, authorId);
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        return chapterRepository.findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(comicId, authorId, safePagination.toPageRequest())
                .map(this::toPreviewResponse);
    }

    @Transactional
    public SubmitChapterReviewResponse submitForReview(UUID comicId, UUID chapterId, UUID authorId) {
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);

        if (!hasImages(chapter)) {
            throw new CustomException(400, "Chapter must have at least one image before review submission", HttpStatus.BAD_REQUEST);
        }

        submissionRepository.findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        chapterId, authorId, "author")
                .ifPresent(lastSubmission -> {
                    String status = lastSubmission.getStatus() == null ? "" : lastSubmission.getStatus().trim().toLowerCase(Locale.ROOT);
                    if ("pending".equals(status)) {
                        throw new CustomException(409, "Chapter has already been submitted for review", HttpStatus.CONFLICT);
                    }
                    if ("approved".equals(status)) {
                        throw new CustomException(409, "Approved chapters cannot be submitted again", HttpStatus.CONFLICT);
                    }
                });

        Date now = new Date();
        SubmissionEntity submission = SubmissionEntity.builder()
                .comicId(comicId)
                .chapterId(chapterId)
                .authorId(authorId)
                .title(comic.getTitle())
                .chapter("Chapter " + chapter.getChapterNumber())
                .submittedBy("Author: " + authorId)
                .queueType("author")
                .timeLabel("Just now")
                .timestamp(now.getTime())
                .words(0)
                .priority("Medium")
                .flags(0)
                .status("pending")
                .cover(firstNonBlank(comic.getThumbnail(), comic.getCover()))
                .content("Chapter " + chapter.getChapterNumber() + " has " + resolvePageCount(chapter) + " image pages waiting for moderation review.")
                .build();
        submissionRepository.save(submission);
        chapter.setModerationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW);
        chapterRepository.save(chapter);

        return SubmitChapterReviewResponse.builder()
                .chapterId(chapter.getId())
                .comicId(comicId)
                .status(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .submittedAt(now)
                .message("Chapter submitted for moderator review")
                .build();
    }

    @Transactional
    public ChapterPreviewResponse updateChapter(UUID comicId, UUID chapterId, UUID authorId, ChapterUploadRequest request) {
        if (request == null) {
            throw new CustomException(400, "Chapter update request is required", HttpStatus.BAD_REQUEST);
        }
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);
        boolean changed = false;

        String requestedChapterNumber = normalizeChapterNumber(request.getChapterNumber());
        if (StringUtils.hasText(requestedChapterNumber) && !requestedChapterNumber.equals(chapter.getChapterNumber())) {
            if (chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, requestedChapterNumber)) {
                throw new CustomException(409, "Chapter number already exists for this comic", HttpStatus.CONFLICT);
            }
            chapter.setChapterNumber(requestedChapterNumber);
            changed = true;
        }

        if (request.getTitle() != null) {
            String title = trimToNull(request.getTitle());
            if (!java.util.Objects.equals(trimToNull(chapter.getTitle()), title)) {
                chapter.setTitle(title);
                changed = true;
            }
        }

        if (changed) {
            if (chapter.getModerationStatus() == ChapterStatus.PUBLISHED) {
                chapter.setModerationStatus(ChapterStatus.SUBMITTED_FOR_REVIEW);
                if (!hasPendingChapterSubmission(chapter.getId(), authorId)) {
                    createChapterReviewSubmission(chapter.getComic(), chapter, authorId);
                }
            } else {
                chapter.setModerationStatus(hasImages(chapter) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT);
            }
        }

        ChapterEntity savedChapter = chapterRepository.save(chapter);
        refreshComicChapterMetadata(savedChapter.getComic());
        return toPreviewResponse(savedChapter);
    }

    @Transactional
    public void deleteChapter(UUID comicId, UUID chapterId, UUID authorId) {
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);
        chapter.setDeleted(true);
        chapterRepository.save(chapter);
        refreshComicChapterMetadata(comic);
    }

    private boolean hasPendingChapterSubmission(UUID chapterId, UUID authorId) {
        return submissionRepository.findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                        chapterId, authorId, "author")
                .map(SubmissionEntity::getStatus)
                .filter(StringUtils::hasText)
                .map(status -> status.trim().toLowerCase(Locale.ROOT))
                .filter("pending"::equals)
                .isPresent();
    }

    private SubmissionEntity createChapterReviewSubmission(ComicEntity comic, ChapterEntity chapter, UUID authorId) {
        Date now = new Date();
        SubmissionEntity submission = SubmissionEntity.builder()
                .comicId(comic.getId())
                .chapterId(chapter.getId())
                .authorId(authorId)
                .title(comic.getTitle())
                .chapter("Chapter " + chapter.getChapterNumber())
                .submittedBy("Author: " + authorId)
                .queueType("author")
                .timeLabel("Just now")
                .timestamp(now.getTime())
                .words(0)
                .priority("Medium")
                .flags(0)
                .status("pending")
                .cover(firstNonBlank(comic.getThumbnail(), comic.getCover()))
                .content("Chapter " + chapter.getChapterNumber() + " has " + resolvePageCount(chapter) + " image pages waiting for moderation review.")
                .build();
        return submissionRepository.save(submission);
    }

    private ChapterEntity getOwnedChapter(UUID comicId, UUID chapterId, UUID authorId) {
        if (comicId == null || chapterId == null || authorId == null) {
            throw new CustomException(400, "Comic id, chapter id, and author id are required", HttpStatus.BAD_REQUEST);
        }
        return chapterRepository.findByIdAndComic_IdAndComic_AuthorIdAndDeletedFalse(chapterId, comicId, authorId)
                .orElseThrow(() -> new CustomException(404, "Chapter not found or does not belong to this author", HttpStatus.NOT_FOUND));
    }

    private void validateUploadRequest(ChapterUploadRequest request) {
        if (request == null) {
            throw new CustomException(400, "Chapter upload request is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getAuthorId() == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new CustomException(400, "Chapter archive file is required", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = zipFile.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !isCbzArchiveFileName(originalFilename)) {
            throw new CustomException(400, "Only .cbz chapter files are accepted", HttpStatus.BAD_REQUEST);
        }

        String fileName = getBaseName(normalizeEntryName(originalFilename));
        if (!CHAPTER_ZIP_NAME_PATTERN.matcher(fileName).matches()) {
            throw new CustomException(
                    400,
                    "Chapter archive name must be like 'Chapter 1.cbz' or 'Chapter 1,5.cbz'. Invalid file: " + fileName,
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private List<ImageCandidate> extractAndValidateImages(MultipartFile zipFile) {
        List<ImageCandidate> images = new ArrayList<>();
        Map<String, Integer> duplicateNameCounter = new HashMap<>();
        int zipOrder = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                zipOrder++;
                String entryName = normalizeEntryName(entry.getName());
                if (!isCandidateFile(entryName)) {
                    continue;
                }

                if (splitCleanPath(entryName).length != 1) {
                    throw new CustomException(400, "Chapter CBZ must contain image files at root only. Invalid entry: " + entryName, HttpStatus.BAD_REQUEST);
                }

                String baseFileName = getBaseName(entryName);
                if (isCbzArchiveFileName(baseFileName)) {
                    throw new CustomException(
                            400,
                            "Upload Chapter only accepts page images. Do not put another archive inside chapter CBZ: " + entryName,
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (!isAllowedImage(baseFileName)) {
                    throw new CustomException(400, "Unsupported image format in chapter archive: " + entryName, HttpStatus.BAD_REQUEST);
                }

                byte[] bytes = readCurrentEntry(zipInputStream);
                if (bytes.length == 0) {
                    throw new CustomException(400, "Empty image file in chapter CBZ: " + entryName, HttpStatus.BAD_REQUEST);
                }
                if (bytes.length > MAX_IMAGE_SIZE_BYTES) {
                    throw new CustomException(400, "Image exceeds 10MB limit: " + entryName, HttpStatus.BAD_REQUEST);
                }

                String safeDisplayName = makeDuplicateSafeDisplayName(baseFileName, duplicateNameCounter);
                ImageDimension dimension = readImageDimension(entryName, bytes);
                images.add(new ImageCandidate(safeDisplayName, entryName, zipOrder, bytes, dimension));

                if (images.size() > maxPages) {
                    throw new CustomException(400, "Chapter archive exceeds maximum page count of " + maxPages, HttpStatus.BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            throw new CustomException(400, "Invalid or unreadable chapter CBZ file", HttpStatus.BAD_REQUEST);
        }

        if (images.isEmpty()) {
            throw new CustomException(400, "Chapter CBZ file does not contain any supported images", HttpStatus.BAD_REQUEST);
        }
        return images;
    }

    private byte[] readCurrentEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zipInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private ImageDimension readImageDimension(String fileName, byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".webp")) {
                    return new ImageDimension(null, null);
                }
                throw new CustomException(400, "Invalid image file in chapter CBZ: " + fileName, HttpStatus.BAD_REQUEST);
            }
            return new ImageDimension(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new CustomException(400, "Invalid image file in chapter CBZ: " + fileName, HttpStatus.BAD_REQUEST);
        }
    }

    private String makeDuplicateSafeDisplayName(String baseFileName, Map<String, Integer> duplicateNameCounter) {
        String normalizedKey = baseFileName.toLowerCase(Locale.ROOT);
        int occurrence = duplicateNameCounter.merge(normalizedKey, 1, Integer::sum);
        if (occurrence <= 1) {
            return baseFileName;
        }

        int dotIndex = baseFileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return baseFileName + "-duplicate-" + occurrence;
        }
        return baseFileName.substring(0, dotIndex) + "-duplicate-" + occurrence + baseFileName.substring(dotIndex);
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

    private boolean isAllowedImage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(extension -> lower.endsWith("." + extension));
    }

    private boolean isCbzArchiveFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".cbz");
    }

    private Comparator<ImageCandidate> imageNaturalComparator() {
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> {
            int comparison = compareNaturally(left.originalFileName(), right.originalFileName(), collator);
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(left.zipOrder(), right.zipOrder());
        };
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
            int comparison;
            if (leftNumber && rightNumber) {
                comparison = Integer.compare(Integer.parseInt(leftPart), Integer.parseInt(rightPart));
            } else {
                comparison = collator.compare(leftPart, rightPart);
            }
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

    private ChapterPreviewResponse toPreviewResponse(ChapterEntity chapter) {
        List<ChapterPageResponse> pageResponses = buildPageResponses(chapter);
        return ChapterPreviewResponse.builder()
                .id(chapter.getId())
                .comicId(chapter.getComic() == null ? null : chapter.getComic().getId())
                .authorId(chapter.getComic() == null ? null : chapter.getComic().getAuthorId())
                .chapterNumber(chapter.getChapterNumber())
                .title(chapter.getTitle())
                .status(resolveChapterStatus(chapter))
                .pageCount(pageResponses.size())
                .createdAt(toDate(chapter.getCreatedAt()))
                .updatedAt(toDate(chapter.getUpdatedAt()))
                .pages(pageResponses)
                .build();
    }

    private List<ChapterPageResponse> buildPageResponses(ChapterEntity chapter) {
        if (chapter.getImages() == null || chapter.getImages().isEmpty()) {
            return List.of();
        }
        List<ChapterPageResponse> responses = new ArrayList<>();
        for (int index = 0; index < chapter.getImages().size(); index++) {
            responses.add(ChapterPageResponse.builder()
                    .pageNumber(index + 1)
                    .imageUrl(chapter.getImages().get(index))
                    .build());
        }
        return responses;
    }

    private String resolveChapterNumber(ChapterUploadRequest request, MultipartFile zipFile) {
        String fileName = getBaseName(normalizeEntryName(zipFile.getOriginalFilename()));
        String numberFromFileName = parseChapterNumberFromFileName(fileName);
        String numberFromRequest = normalizeChapterNumber(request.getChapterNumber());

        if (!StringUtils.hasText(numberFromRequest)) {
            return numberFromFileName;
        }

        if (!numberFromRequest.equals(numberFromFileName)) {
            throw new CustomException(
                    400,
                    "Chapter number does not match archive filename. Form value: "
                            + request.getChapterNumber()
                            + ", archive filename: "
                            + fileName,
                    HttpStatus.BAD_REQUEST
            );
        }

        return numberFromFileName;
    }

    private String parseChapterNumberFromFileName(String fileName) {
        Matcher matcher = CHAPTER_ZIP_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).replace(',', '.');
    }

    private String normalizeChapterNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim().replace(',', '.');
        if (!CHAPTER_NUMBER_PATTERN.matcher(normalized).matches()) {
            throw new CustomException(
                    400,
                    "Chapter number must be like 1 or 1,5",
                    HttpStatus.BAD_REQUEST
            );
        }

        return normalized;
    }

    private String getBaseName(String entryName) {
        if (entryName == null) {
            return "";
        }
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

    private ChapterStatus resolveChapterStatus(ChapterEntity chapter) {
        if (chapter.getModerationStatus() != null) {
            return chapter.getModerationStatus();
        }
        UUID chapterId = chapter.getId();
        UUID authorId = chapter.getComic() == null ? null : chapter.getComic().getAuthorId();
        if (chapterId != null && authorId != null) {
            return submissionRepository.findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                            chapterId, authorId, "author")
                    .map(this::mapSubmissionStatusToChapterStatus)
                    .orElseGet(() -> hasImages(chapter) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT);
        }
        return hasImages(chapter) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT;
    }

    private ChapterStatus mapSubmissionStatusToChapterStatus(SubmissionEntity submission) {
        if (submission == null || !StringUtils.hasText(submission.getStatus())) {
            return ChapterStatus.DRAFT;
        }
        return switch (submission.getStatus().trim().toLowerCase(Locale.ROOT)) {
            case "pending" -> ChapterStatus.SUBMITTED_FOR_REVIEW;
            case "approved" -> ChapterStatus.APPROVED;
            case "rejected" -> ChapterStatus.REJECTED;
            default -> ChapterStatus.DRAFT;
        };
    }

    private boolean hasImages(ChapterEntity chapter) {
        return chapter.getImages() != null && !chapter.getImages().isEmpty();
    }

    private int resolvePageCount(ChapterEntity chapter) {
        return chapter.getImages() == null ? 0 : chapter.getImages().size();
    }

    private void refreshComicChapterMetadata(ComicEntity comic) {
        if (comic == null || comic.getId() == null) {
            return;
        }
        List<ChapterEntity> chapters = chapterRepository.findAllByComic_IdAndDeletedFalse(comic.getId());
        comic.setChapterCount(chapters.size());
        chapters.stream()
                .filter(chapter -> StringUtils.hasText(chapter.getChapterNumber()))
                .max(Comparator.comparing(chapter -> toChapterSortNumber(chapter.getChapterNumber())))
                .ifPresent(chapter -> {
                    comic.setLatestChapterNumber(chapter.getChapterNumber());
                    comic.setLastChapterUpdatedAt(chapter.getUpdatedAt() != null ? chapter.getUpdatedAt() : Instant.now());
                });
        if (chapters.isEmpty()) {
            comic.setLatestChapterNumber(null);
        }
        comicRepository.save(comic);
    }

    private BigDecimal toChapterSortNumber(String chapterNumber) {
        try {
            return new BigDecimal(chapterNumber.replace(',', '.'));
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String buildOrderedFileName(int pageNumber, String originalFileName) {
        String fileName = originalFileName;
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        return String.format("%03d-%s", pageNumber, fileName);
    }

    private Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private record ImageCandidate(String originalFileName, String originalEntryName, int zipOrder, byte[] bytes, ImageDimension dimension) {
    }

    private record ImageDimension(Integer width, Integer height) {
    }
}
