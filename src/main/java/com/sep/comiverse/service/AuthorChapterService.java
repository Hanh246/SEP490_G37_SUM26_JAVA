package com.sep.comiverse.service;

import com.sep.comiverse.dto.ReadingHistoryCacheDTO;
import com.sep.comiverse.dto.pagination.PaginationSearchDTO;
import com.sep.comiverse.dto.request.ChapterUploadRequest;
import com.sep.comiverse.dto.response.ChapterPageResponse;
import com.sep.comiverse.dto.response.ChapterPreviewResponse;
import com.sep.comiverse.dto.response.SubmitChapterReviewResponse;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.SubmissionEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.plugin.crud.ChapterCrudPlugin;
import com.sep.comiverse.plugin.crud.ComicCrudPlugin;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IComicRepository;
import com.sep.comiverse.repository.IReadingHistoryRepository;
import com.sep.comiverse.repository.ISubmissionRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.service.scheduler.ViewSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthorChapterService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern NATURAL_PART_PATTERN = Pattern.compile("\\d+|\\D+");
    private static final Pattern CHAPTER_NUMBER_PATTERN =
            Pattern.compile("^[1-9][0-9]*(?:[,.][0-9]+)?$");

    private final AuthorComicService authorComicService;
    private final IComicRepository comicRepository;
    private final IChapterRepository chapterRepository;
    private final ISubmissionRepository submissionRepository;
    private final IReadingHistoryRepository readingHistoryRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final ChapterCrudPlugin chapterCrudPlugin;
    private final ComicCrudPlugin comicCrudPlugin;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final NotificationService notificationService;
    private final ChapterPremiumPolicyService chapterPremiumPolicyService;
    private final AuthorLicenseService authorLicenseService;

    @Value("${author.chapter.max-pages:200}")
    private int maxPages;

    @Value("${author.chapter.max-total-upload-size-bytes:104857600}")
    private long maxTotalUploadSizeBytes;

    private String computeContentHash(List<ImageCandidate> images) {
        if (images == null || images.isEmpty()) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            for (ImageCandidate img : images) {
                md.update(img.bytes());
            }
            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public ChapterPreviewResponse uploadChapterFolder(
            UUID comicId,
            ChapterUploadRequest request,
            List<MultipartFile> files,
            List<String> relativePaths
    ) {
        validateUploadRequest(request);
        authorLicenseService.assertPublishingAllowed(request.getAuthorId());
        String chapterNumber = normalizeChapterNumber(request.getChapterNumber());
        if (!StringUtils.hasText(chapterNumber)) {
            throw new CustomException(400, "Chapter number is required", HttpStatus.BAD_REQUEST);
        }

        ComicEntity comic = authorComicService.getOwnedComic(comicId, request.getAuthorId());
        if (chapterRepository.existsByComic_IdAndChapterNumberAndDeletedFalse(comicId, chapterNumber)) {
            throw new CustomException(409, "Chapter number already exists for this comic", HttpStatus.CONFLICT);
        }

        List<ImageCandidate> images = validateAndReadFolderImages(files, relativePaths);
        images.sort(imageNaturalComparator());

        String contentHash = computeContentHash(images);
        if (contentHash != null
                && chapterRepository.existsByContentHashAndModerationStatus(contentHash, ChapterStatus.REJECTED)) {
            throw new CustomException(
                    400,
                    "This chapter content was previously rejected and cannot be re-uploaded. Please contact moderation.",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<String> imageUrls = uploadImages(comicId, chapterNumber, images);

        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .chapterNumber(chapterNumber)
                .title(trimToNull(request.getTitle()))
                .moderationStatus(ChapterStatus.PREVIEW_READY)
                .isPremium(chapterPremiumPolicyService.isPremiumChapter(chapterNumber))
                .images(imageUrls)
                .contentHash(contentHash)
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
        authorLicenseService.assertPublishingAllowed(authorId);
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);

        if (!hasImages(chapter)) {
            throw new CustomException(400, "Chapter must have at least one image before review submission", HttpStatus.BAD_REQUEST);
        }

        if (chapter.getModerationStatus() == ChapterStatus.SUBMITTED_FOR_REVIEW) {
            throw new CustomException(409, "Chapter has already been submitted for review", HttpStatus.CONFLICT);
        }
        if (chapter.getModerationStatus() == ChapterStatus.PUBLISHED) {
            throw new CustomException(409, "Approved chapters cannot be submitted again", HttpStatus.CONFLICT);
        }

        // Content Fingerprinting: Block exact duplicates of previously rejected content
        if (chapter.getContentHash() != null && chapterRepository.existsByContentHashAndModerationStatus(chapter.getContentHash(), ChapterStatus.REJECTED)) {
            // Only block if THIS chapter itself isn't the rejected one we are trying to resubmit (wait, if THIS chapter is the rejected one, its contentHash matches!
            // Wait, if they are resubmitting a rejected chapter WITHOUT modifying it, the hash is the same! We MUST block it.)
            throw new CustomException(409, "This chapter content was previously rejected. You must modify the images (fix the issues) before resubmitting.", HttpStatus.CONFLICT);
        }

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
                .cover(comic.getCover())
                .content("Chapter " + chapter.getChapterNumber() + " has " + resolvePageCount(chapter) + " image pages waiting for moderation review.")
                .chapterImages(copyImages(chapter))
                .pageCount(resolvePageCount(chapter))
                .build();
        submissionRepository.save(submission);
        notifyModeratorsAboutChapter(comic, chapter);
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
            chapter.setIsPremium(chapterPremiumPolicyService.isPremiumChapter(requestedChapterNumber));
            changed = true;
        }

        if (request.getTitle() != null) {
            String title = trimToNull(request.getTitle());
            if (!Objects.equals(trimToNull(chapter.getTitle()), title)) {
                chapter.setTitle(title);
                changed = true;
            }
        }

        if (changed) {
            // Metadata shown to readers is moderation-sensitive. Editing a pending or
            // published chapter therefore cancels the stale review and returns the
            // chapter to preview so the author can submit the updated version again.
            cancelPendingChapterSubmissions(chapterId, authorId);
            chapter.setModerationStatus(hasImages(chapter) ? ChapterStatus.PREVIEW_READY : ChapterStatus.DRAFT);
            chapter.setRejectionReason(null);
            chapter.setRejectedById(null);
        }

        ChapterEntity savedChapter = chapterRepository.save(chapter);
        refreshComicChapterMetadata(savedChapter.getComic());
        return toPreviewResponse(savedChapter);
    }

    @Transactional
    public void deleteChapter(UUID comicId, UUID chapterId, UUID authorId) {
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);

        // Author deletion is intentionally a HARD DELETE. Remove dependent rows
        // first so the chapter row can be physically deleted without FK errors
        // and without leaving orphaned histories or moderation submissions.
        removeQueuedReadingHistory(chapterId);
        teamTaskRepository.hardDeleteAllByChapterId(chapterId);
        readingHistoryRepository.hardDeleteAllByChapterId(chapterId);
        submissionRepository.hardDeleteAllByChapterId(chapterId);
        chapterRepository.delete(chapter);
        chapterRepository.flush();

        evictDeletedChapterCaches(comicId, chapterId);
        refreshComicChapterMetadata(comic);
        authorComicService.revokeComicProfileSubmissionIfEmpty(comicId, authorId);
    }

    private void evictDeletedChapterCaches(UUID comicId, UUID chapterId) {
        chapterCrudPlugin.evictChaptersCache(comicId);
        chapterCrudPlugin.evictChapterDetailCache(chapterId);
        comicCrudPlugin.evictComicCache(comicId);
        try {
            redisTemplate.opsForHash().delete(ViewSyncScheduler.CHAPTER_VIEW_HASH, chapterId.toString());
            removeQueuedReadingHistory(chapterId);
        } catch (Exception ignored) {
            // Redis must not prevent the database hard delete from completing.
        }
    }

    private void removeQueuedReadingHistory(UUID chapterId) {
        try {
            Set<Object> queuedEntries = redisTemplate.opsForSet()
                    .members(ReadingHistoryService.READING_HISTORY_SYNC_QUEUE);
            if (queuedEntries == null || queuedEntries.isEmpty()) {
                return;
            }
            queuedEntries.stream()
                    .filter(ReadingHistoryCacheDTO.class::isInstance)
                    .map(ReadingHistoryCacheDTO.class::cast)
                    .filter(entry -> chapterId.equals(entry.getChapterId()))
                    .forEach(entry -> redisTemplate.opsForSet()
                            .remove(ReadingHistoryService.READING_HISTORY_SYNC_QUEUE, entry));
        } catch (Exception ignored) {
            // Redis cleanup is best-effort; database consistency remains primary.
        }
    }

    @Transactional
    public ChapterPreviewResponse replaceChapterFolder(
            UUID comicId,
            UUID chapterId,
            UUID authorId,
            List<MultipartFile> files,
            List<String> relativePaths
    ) {
        authorLicenseService.assertPublishingAllowed(authorId);
        ComicEntity comic = authorComicService.getOwnedComic(comicId, authorId);
        ChapterEntity chapter = getOwnedChapter(comicId, chapterId, authorId);

        List<ImageCandidate> images = validateAndReadFolderImages(files, relativePaths);
        images.sort(imageNaturalComparator());

        String contentHash = computeContentHash(images);
        if (contentHash != null
                && chapterRepository.existsByContentHashAndModerationStatus(contentHash, ChapterStatus.REJECTED)) {
            throw new CustomException(
                    400,
                    "This chapter content was previously rejected and cannot be re-uploaded. Please contact moderation.",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<String> imageUrls = uploadImages(comicId, chapter.getChapterNumber(), images);
        chapter.setImages(imageUrls);
        chapter.setContentHash(contentHash);
        chapter.setModerationStatus(ChapterStatus.PREVIEW_READY);
        chapter.setRejectionReason(null);
        chapter.setRejectedById(null);
        cancelPendingChapterSubmissions(chapterId, authorId);

        ChapterEntity savedChapter = chapterRepository.save(chapter);
        refreshComicChapterMetadata(comic);
        return toPreviewResponse(savedChapter);
    }

    private void cancelPendingChapterSubmissions(UUID chapterId, UUID authorId) {
        submissionRepository.findAllByChapterIdAndAuthorIdAndQueueTypeIgnoreCaseAndStatusIgnoreCaseAndDeletedFalse(
                        chapterId, authorId, "author", "pending")
                .forEach(submission -> {
                    submission.setStatus("cancelled");
                    submission.setDeleted(true);
                    submissionRepository.save(submission);
                });
    }

    private void refreshComicChapterMetadata(ComicEntity comic) {
        if (comic == null || comic.getId() == null) {
            return;
        }

        List<ChapterEntity> publishedChapters = chapterRepository
                .findAllByComic_IdAndDeletedFalseAndModerationStatus(comic.getId(), ChapterStatus.PUBLISHED);

        comic.setChapterCount(publishedChapters.size());
        publishedChapters.stream()
                .filter(chapter -> StringUtils.hasText(chapter.getChapterNumber()))
                .max(Comparator.comparing(chapter -> toChapterSortNumber(chapter.getChapterNumber())))
                .ifPresentOrElse(chapter -> {
                    comic.setLatestChapterNumber(chapter.getChapterNumber());
                    comic.setLastChapterUpdatedAt(chapter.getUpdatedAt() != null ? chapter.getUpdatedAt() : Instant.now());
                }, () -> {
                    comic.setLatestChapterNumber(null);
                    comic.setLastChapterUpdatedAt(null);
                });

        comicRepository.save(comic);
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
                .cover(comic.getCover())
                .content("Chapter " + chapter.getChapterNumber() + " has " + resolvePageCount(chapter) + " image pages waiting for moderation review.")
                .chapterImages(copyImages(chapter))
                .pageCount(resolvePageCount(chapter))
                .build();
        SubmissionEntity saved = submissionRepository.save(submission);
        notifyModeratorsAboutChapter(comic, chapter);
        return saved;
    }

    private void notifyModeratorsAboutChapter(ComicEntity comic, ChapterEntity chapter) {
        notificationService.notifyModeratorsWithLanguage(
                comic.getLanguage(),
                "New chapter review",
                comic.getTitle() + " - Chapter " + chapter.getChapterNumber() + " is waiting for moderation.",
                "UPDATE",
                NotificationPreferenceKey.REVIEW_QUEUE
        );
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

    private List<ImageCandidate> validateAndReadFolderImages(
            List<MultipartFile> files,
            List<String> relativePaths
    ) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(400, "Chapter folder must contain at least one image", HttpStatus.BAD_REQUEST);
        }
        if (relativePaths == null || relativePaths.isEmpty()) {
            throw new CustomException(400, "Chapter folder relative paths are required", HttpStatus.BAD_REQUEST);
        }
        if (files.size() != relativePaths.size()) {
            throw new CustomException(
                    400,
                    "The number of uploaded files must match relativePathsJson",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (files.size() > maxPages) {
            throw new CustomException(
                    400,
                    "Chapter folder exceeds maximum page count of " + maxPages,
                    HttpStatus.BAD_REQUEST
            );
        }

        List<ImageCandidate> images = new ArrayList<>();
        Map<String, Integer> duplicateNameCounter = new HashMap<>();
        String selectedFolder = null;
        long totalBytes = 0L;

        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String relativePath = normalizeEntryName(relativePaths.get(index));

            if (file == null || file.isEmpty()) {
                throw new CustomException(400, "Empty image file at position " + (index + 1), HttpStatus.BAD_REQUEST);
            }
            if (!StringUtils.hasText(relativePath)) {
                throw new CustomException(400, "Empty relative path at position " + (index + 1), HttpStatus.BAD_REQUEST);
            }
            if (relativePath.startsWith("/") || relativePath.contains("../") || relativePath.contains("..\\")) {
                throw new CustomException(400, "Unsafe folder path: " + relativePath, HttpStatus.BAD_REQUEST);
            }
            if (isHiddenPath(relativePath)) {
                throw new CustomException(400, "Hidden files are not allowed in chapter folder: " + relativePath, HttpStatus.BAD_REQUEST);
            }

            String[] parts = splitCleanPath(relativePath);
            if (parts.length != 2) {
                throw new CustomException(
                        400,
                        "Images must be directly inside one chapter folder: " + relativePath,
                        HttpStatus.BAD_REQUEST
                );
            }
            if (!StringUtils.hasText(selectedFolder)) {
                selectedFolder = parts[0];
            } else if (!selectedFolder.equals(parts[0])) {
                throw new CustomException(
                        400,
                        "Do not combine files from multiple chapter folders",
                        HttpStatus.BAD_REQUEST
                );
            }

            String baseFileName = getBaseName(relativePath);
            String uploadedName = getBaseName(normalizeEntryName(file.getOriginalFilename()));
            if (StringUtils.hasText(uploadedName) && !baseFileName.equals(uploadedName)) {
                throw new CustomException(
                        400,
                        "Uploaded filename does not match relative path: " + relativePath,
                        HttpStatus.BAD_REQUEST
                );
            }
            if (!isAllowedImage(baseFileName)) {
                throw new CustomException(
                        400,
                        "Unsupported image format in chapter folder: " + relativePath,
                        HttpStatus.BAD_REQUEST
                );
            }
            if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
                throw new CustomException(400, "Image exceeds 10MB limit: " + relativePath, HttpStatus.BAD_REQUEST);
            }

            totalBytes += file.getSize();
            if (totalBytes > maxTotalUploadSizeBytes) {
                throw new CustomException(
                        400,
                        "Chapter folder exceeds the " + (maxTotalUploadSizeBytes / (1024L * 1024L)) + "MB total upload limit",
                        HttpStatus.BAD_REQUEST
                );
            }

            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException error) {
                throw new CustomException(400, "Could not read image file: " + relativePath, HttpStatus.BAD_REQUEST);
            }
            if (bytes.length == 0) {
                throw new CustomException(400, "Empty image file: " + relativePath, HttpStatus.BAD_REQUEST);
            }

            String safeDisplayName = makeDuplicateSafeDisplayName(baseFileName, duplicateNameCounter);
            ImageDimension dimension = readImageDimension(relativePath, bytes);
            images.add(new ImageCandidate(safeDisplayName, relativePath, index, bytes, dimension));
        }

        return images;
    }

    private List<String> uploadImages(UUID comicId, String chapterNumber, List<ImageCandidate> images) {
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
        return imageUrls;
    }

    private ImageDimension readImageDimension(String fileName, byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".webp")) {
                    return new ImageDimension(null, null);
                }
                throw new CustomException(400, "Invalid image file in chapter folder: " + fileName, HttpStatus.BAD_REQUEST);
            }
            return new ImageDimension(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new CustomException(400, "Invalid image file in chapter folder: " + fileName, HttpStatus.BAD_REQUEST);
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

    private boolean isHiddenPath(String path) {
        String normalized = normalizeEntryName(path);
        if (normalized.startsWith("__MACOSX/")) {
            return true;
        }
        for (String part : normalized.split("/")) {
            if (part.startsWith(".")) {
                return true;
            }
        }
        return false;
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
        return (left, right) -> {
            int comparison = compareNaturally(left.originalFileName(), right.originalFileName(), collator);
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(left.sourceOrder(), right.sourceOrder());
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
        List<String> previewImages = resolvePreviewImages(chapter);
        List<ChapterPageResponse> pageResponses = buildPageResponses(previewImages);
        int persistedPageCount = chapter.getPageCount() == null ? 0 : chapter.getPageCount();
        int visiblePageCount = Math.max(persistedPageCount, pageResponses.size());
        return ChapterPreviewResponse.builder()
                .id(chapter.getId())
                .comicId(chapter.getComic() == null ? null : chapter.getComic().getId())
                .authorId(chapter.getComic() == null ? null : chapter.getComic().getAuthorId())
                .chapterNumber(chapter.getChapterNumber())
                .title(chapter.getTitle())
                .status(resolveChapterStatus(chapter))
                .rejectionReason(chapter.getRejectionReason())
                .pageCount(visiblePageCount)
                .viewCount(chapter.getViewCount() == null ? 0L : chapter.getViewCount())
                .createdAt(toDate(chapter.getCreatedAt()))
                .updatedAt(toDate(chapter.getUpdatedAt()))
                .pages(pageResponses)
                .build();
    }

    private List<ChapterPageResponse> buildPageResponses(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<ChapterPageResponse> responses = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            responses.add(ChapterPageResponse.builder()
                    .pageNumber(index + 1)
                    .imageUrl(images.get(index))
                    .build());
        }
        return responses;
    }

    /**
     * Prefer the live chapter row, but keep moderation preview resilient by falling
     * back to the immutable submission evidence snapshot for rejected chapters.
     */
    private List<String> resolvePreviewImages(ChapterEntity chapter) {
        if (chapter != null && chapter.getImages() != null && !chapter.getImages().isEmpty()) {
            return new ArrayList<>(chapter.getImages());
        }
        if (chapter == null || chapter.getId() == null) {
            return List.of();
        }
        return submissionRepository
                .findTopByChapterIdAndQueueTypeIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(chapter.getId(), "author")
                .map(SubmissionEntity::getChapterImages)
                .filter(images -> images != null && !images.isEmpty())
                .map(images -> (List<String>) new ArrayList<>(images))
                .orElseGet(List::of);
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

    private List<String> copyImages(ChapterEntity chapter) {
        if (chapter == null || chapter.getImages() == null || chapter.getImages().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chapter.getImages());
    }

    private int resolvePageCount(ChapterEntity chapter) {
        return chapter.getImages() == null ? 0 : chapter.getImages().size();
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

    private record ImageCandidate(String originalFileName, String originalEntryName, int sourceOrder, byte[] bytes, ImageDimension dimension) {
    }

    private record ImageDimension(Integer width, Integer height) {
    }
}