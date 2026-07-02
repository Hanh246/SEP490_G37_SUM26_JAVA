package com.sep.comiverse.service;

import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.ChapterPageResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ChapterPageEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterPageRepository;
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
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

    private final AuthorComicService authorComicService;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final IChapterPageRepository chapterPageRepository;
    private final ISubmissionRepository submissionRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    @Value("${author.chapter.max-pages:200}")
    private int maxPages;

    @Transactional
    public ChapterPreviewResponse uploadChapterZip(UUID comicId, ChapterUploadRequest request, MultipartFile zipFile) {
        validateUploadRequest(request);
        validateZipFile(zipFile);

        ComicEntity comic = authorComicService.getOwnedComic(comicId, request.getAuthorId());
        String chapterNumber = String.valueOf(request.getChapterNumber());
        if (chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, chapterNumber)) {
            throw new CustomException(409, "Chapter number already exists for this comic", HttpStatus.CONFLICT);
        }

        List<ImageCandidate> images = extractAndValidateImages(zipFile);
        images.sort(imageNaturalComparator());

        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber(chapterNumber)
                .title(trimToNull(request.getTitle()))
                .images(new ArrayList<>())
                .build();
        ChapterEntity savedChapter = chapterRepository.save(chapter);

        List<ChapterPageEntity> pages = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            ImageCandidate image = images.get(index);
            String targetFolder = "comiverse/chapters/" + comicId + "/chapter-" + chapterNumber;
            CloudinaryUploadResult upload = cloudinaryStorageService.uploadImage(
                    image.bytes(),
                    buildOrderedFileName(index + 1, image.originalFileName()),
                    targetFolder
            );

            ChapterPageEntity page = ChapterPageEntity.builder()
                    .comicId(comic.getId())
                    .chapterId(savedChapter.getId())
                    .pageNumber(index + 1)
                    .imageUrl(upload.getSecureUrl())
                    .cloudinaryPublicId(upload.getPublicId())
                    .originalFileName(image.originalFileName())
                    .fileSizeBytes(upload.getBytes() != null ? upload.getBytes() : (long) image.bytes().length)
                    .width(upload.getWidth() != null ? upload.getWidth() : image.dimension().width())
                    .height(upload.getHeight() != null ? upload.getHeight() : image.dimension().height())
                    .build();
            pages.add(chapterPageRepository.save(page));
            imageUrls.add(upload.getSecureUrl());
        }

        savedChapter.setImages(imageUrls);
        savedChapter = chapterRepository.save(savedChapter);

        comic.setLatestChapterNumber(chapterNumber);
        comic.setLastChapterUpdatedAt(Instant.now());
        long chapterCount = chapterRepository.countByComic_IdAndDeletedFalse(comic.getId());
        comic.setChapterCount(chapterCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) chapterCount);
        comicRepository.save(comic);

        return toPreviewResponse(savedChapter, pages);
    }

    @Transactional(readOnly = true)
    public ChapterPreviewResponse previewChapter(UUID comicId, UUID chapterId, UUID authorId) {
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);
        List<ChapterPageEntity> pages = chapterPageRepository.findAllByChapterIdAndDeletedFalseOrderByPageNumberAsc(chapterId);
        return toPreviewResponse(chapter, pages);
    }

    @Transactional(readOnly = true)
    public Page<ChapterPreviewResponse> listChapters(UUID comicId, UUID authorId, PaginationSearchDTO pagination) {
        authorComicService.getOwnedComic(comicId, authorId);
        PaginationSearchDTO safePagination = pagination != null ? pagination : new PaginationSearchDTO();
        return chapterRepository.findAllByComic_IdAndComic_AuthorIdAndDeletedFalse(comicId, authorId, safePagination.toPageRequest())
                .map(chapter -> toPreviewResponse(
                        chapter,
                        chapterPageRepository.findAllByChapterIdAndDeletedFalseOrderByPageNumberAsc(chapter.getId())
                ));
    }

    @Transactional
    public SubmitChapterReviewResponse submitForReview(UUID comicId, UUID chapterId, UUID authorId) {
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);
        List<ChapterPageEntity> pages = chapterPageRepository.findAllByChapterIdAndDeletedFalseOrderByPageNumberAsc(chapterId);

        if (pages.isEmpty() && (chapter.getImages() == null || chapter.getImages().isEmpty())) {
            throw new CustomException(400, "Chapter must have at least one page before review submission", HttpStatus.BAD_REQUEST);
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
                .content("Chapter " + chapter.getChapterNumber() + " has " + resolvePageCount(chapter, pages) + " image pages waiting for moderation review.")
                .build();
        submissionRepository.save(submission);

        return SubmitChapterReviewResponse.builder()
                .chapterId(chapter.getId())
                .comicId(comicId)
                .status(ChapterStatus.SUBMITTED_FOR_REVIEW)
                .submittedAt(now)
                .message("Chapter submitted for moderator review")
                .build();
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
        if (request.getChapterNumber() == null || request.getChapterNumber() < 1) {
            throw new CustomException(400, "Chapter number must be at least 1", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new CustomException(400, "ZIP file is required", HttpStatus.BAD_REQUEST);
        }
        String originalFilename = zipFile.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new CustomException(400, "Only .zip chapter files are accepted", HttpStatus.BAD_REQUEST);
        }
    }

    private List<ImageCandidate> extractAndValidateImages(MultipartFile zipFile) {
        List<ImageCandidate> images = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = normalizeEntryName(entry.getName());
                if (!isCandidateFile(entryName)) {
                    continue;
                }
                if (!isAllowedImage(entryName)) {
                    throw new CustomException(400, "Unsupported image format in ZIP: " + entryName, HttpStatus.BAD_REQUEST);
                }

                byte[] bytes = readCurrentEntry(zipInputStream);
                if (bytes.length == 0) {
                    throw new CustomException(400, "Empty image file in ZIP: " + entryName, HttpStatus.BAD_REQUEST);
                }
                if (bytes.length > MAX_IMAGE_SIZE_BYTES) {
                    throw new CustomException(400, "Image exceeds 10MB limit: " + entryName, HttpStatus.BAD_REQUEST);
                }

                ImageDimension dimension = readImageDimension(entryName, bytes);
                images.add(new ImageCandidate(entryName, bytes, dimension));

                if (images.size() > maxPages) {
                    throw new CustomException(400, "Chapter ZIP exceeds maximum page count of " + maxPages, HttpStatus.BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            throw new CustomException(400, "Invalid or unreadable ZIP file", HttpStatus.BAD_REQUEST);
        }

        if (images.isEmpty()) {
            throw new CustomException(400, "ZIP file does not contain any supported images", HttpStatus.BAD_REQUEST);
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
                throw new CustomException(400, "Invalid image file in ZIP: " + fileName, HttpStatus.BAD_REQUEST);
            }
            return new ImageDimension(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new CustomException(400, "Invalid image file in ZIP: " + fileName, HttpStatus.BAD_REQUEST);
        }
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
            throw new CustomException(400, "Unsafe ZIP entry path: " + entryName, HttpStatus.BAD_REQUEST);
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

    private Comparator<ImageCandidate> imageNaturalComparator() {
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> compareNaturally(left.originalFileName(), right.originalFileName(), collator);
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

    private ChapterPreviewResponse toPreviewResponse(ChapterEntity chapter, List<ChapterPageEntity> storedPages) {
        List<ChapterPageResponse> pageResponses = buildPageResponses(chapter, storedPages);
        return ChapterPreviewResponse.builder()
                .id(chapter.getId())
                .comicId(chapter.getComic() == null ? null : chapter.getComic().getId())
                .authorId(chapter.getComic() == null ? null : chapter.getComic().getAuthorId())
                .chapterNumber(parseChapterNumber(chapter.getChapterNumber()))
                .title(chapter.getTitle())
                .status(resolveChapterStatus(chapter, storedPages))
                .pageCount(pageResponses.size())
                .createdAt(toDate(chapter.getCreatedAt()))
                .updatedAt(toDate(chapter.getUpdatedAt()))
                .pages(pageResponses)
                .build();
    }

    private List<ChapterPageResponse> buildPageResponses(ChapterEntity chapter, List<ChapterPageEntity> storedPages) {
        if (storedPages != null && !storedPages.isEmpty()) {
            return storedPages.stream().map(this::toPageResponse).toList();
        }
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

    private ChapterPageResponse toPageResponse(ChapterPageEntity page) {
        return ChapterPageResponse.builder()
                .id(page.getId())
                .pageNumber(page.getPageNumber())
                .imageUrl(page.getImageUrl())
                .originalFileName(page.getOriginalFileName())
                .fileSizeBytes(page.getFileSizeBytes())
                .width(page.getWidth())
                .height(page.getHeight())
                .build();
    }

    private ChapterStatus resolveChapterStatus(ChapterEntity chapter, List<ChapterPageEntity> storedPages) {
        UUID chapterId = chapter.getId();
        UUID authorId = chapter.getComic() == null ? null : chapter.getComic().getAuthorId();
        if (chapterId != null && authorId != null) {
            return submissionRepository.findTopByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(
                            chapterId, authorId, "author")
                    .map(this::mapSubmissionStatusToChapterStatus)
                    .orElseGet(() -> hasPages(chapter, storedPages) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT);
        }
        return hasPages(chapter, storedPages) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT;
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

    private boolean hasPages(ChapterEntity chapter, List<ChapterPageEntity> storedPages) {
        return (storedPages != null && !storedPages.isEmpty()) || (chapter.getImages() != null && !chapter.getImages().isEmpty());
    }

    private int resolvePageCount(ChapterEntity chapter, List<ChapterPageEntity> storedPages) {
        if (storedPages != null && !storedPages.isEmpty()) {
            return storedPages.size();
        }
        return chapter.getImages() == null ? 0 : chapter.getImages().size();
    }

    private Integer parseChapterNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return null;
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

    private record ImageCandidate(String originalFileName, byte[] bytes, ImageDimension dimension) {
    }

    private record ImageDimension(Integer width, Integer height) {
    }
}
