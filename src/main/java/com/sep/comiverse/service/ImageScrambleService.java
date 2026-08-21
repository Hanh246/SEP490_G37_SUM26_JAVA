package com.sep.comiverse.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

/**
 * Service xử lý xáo trộn mảnh ảnh truyện tranh (Image Slicing & Scrambling)
 * và mã hóa mảng thứ tự vị trí (Mapping) bằng thuật toán AES-128 CBC Base64.
 */
@Slf4j
@Service
public class ImageScrambleService {

    @Value("${app.scramble.secret-key:ComiVerseKey16B!}")
    private String secretKey;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sep.comiverse.repository.IChapterRepository chapterRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CloudinaryStorageService cloudinaryStorageService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrambleResult {
        private BufferedImage scrambledImage;
        private byte[] scrambledBytes;
        private int[] scrambledOrder;
        private String encryptedMapping;
        private int rows;
        private int cols;
    }

    /**
     * BƯỚC 1: Xử lý chia ảnh gốc thành rows x cols mảnh nhỏ, hoán đổi ngẫu nhiên vị trí (Fisher-Yates)
     * và vẽ ra ảnh bị xáo trộn bằng Graphics2D.
     */
    public ScrambleResult processAndScramble(BufferedImage sourceImage, int rows, int cols) {
        if (sourceImage == null) {
            throw new IllegalArgumentException("Source image cannot be null");
        }
        if (rows <= 0 || cols <= 0) {
            rows = 4;
            cols = 4;
        }

        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        int totalTiles = rows * cols;

        // 1. Khởi tạo mảng thứ tự vị trí ban đầu [0, 1, 2, ..., totalTiles - 1]
        int[] scrambledOrder = new int[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            scrambledOrder[i] = i;
        }

        // 2. Thuật toán Fisher-Yates Shuffle hoán đổi vị trí mảng
        fisherYatesShuffle(scrambledOrder);

        // 3. Tạo BufferedImage mới chứa ảnh bị xáo trộn
        int imageType = sourceImage.getType() == BufferedImage.TYPE_CUSTOM 
                ? BufferedImage.TYPE_INT_ARGB 
                : sourceImage.getType();
        if (imageType == 0) {
            imageType = BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage scrambledImage = new BufferedImage(width, height, imageType);
        Graphics2D g2d = scrambledImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int tileWidth = width / cols;
        int tileHeight = height / rows;

        for (int k = 0; k < totalTiles; k++) {
            int destCol = k % cols;
            int destRow = k / cols;
            int destX1 = destCol * tileWidth;
            int destY1 = destRow * tileHeight;
            int destX2 = (destCol == cols - 1) ? width : destX1 + tileWidth;
            int destY2 = (destRow == rows - 1) ? height : destY1 + tileHeight;

            int origIndex = scrambledOrder[k];
            int origCol = origIndex % cols;
            int origRow = origIndex / cols;
            int srcX1 = origCol * tileWidth;
            int srcY1 = origRow * tileHeight;
            int srcX2 = (origCol == cols - 1) ? width : srcX1 + tileWidth;
            int srcY2 = (origRow == rows - 1) ? height : srcY1 + tileHeight;

            g2d.drawImage(sourceImage,
                    destX1, destY1, destX2, destY2,
                    srcX1, srcY1, srcX2, srcY2,
                    null);
        }

        g2d.dispose();

        // 4. Mã hóa mảng mapping thứ tự thành chuỗi AES-128 CBC Base64
        String encryptedMapping = encryptMapping(scrambledOrder, secretKey);

        byte[] scrambledBytes = null;
        try {
            scrambledBytes = convertToByteArray(scrambledImage, "jpg");
        } catch (IOException e) {
            log.error("Lỗi khi convert scrambledImage sang byte array: {}", e.getMessage());
        }

        return ScrambleResult.builder()
                .scrambledImage(scrambledImage)
                .scrambledBytes(scrambledBytes)
                .scrambledOrder(scrambledOrder)
                .encryptedMapping(encryptedMapping)
                .rows(rows)
                .cols(cols)
                .build();
    }

    /**
     * Nhận mảng byte ảnh thô từ MultipartFile, thực hiện xáo trộn và trả về ScrambleResult chứa byte[] ảnh đã xáo trộn.
     */
    public ScrambleResult processAndScrambleBytes(byte[] imageBytes, int rows, int cols) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (sourceImage == null) {
                log.warn("Không thể đọc dữ liệu ảnh từ byte array");
                return null;
            }
            return processAndScramble(sourceImage, rows, cols);
        } catch (Exception e) {
            log.error("Lỗi khi xáo trộn byte array ảnh: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Nhận URL ảnh từ xa (HTTP/HTTPS/DataURL), tải ảnh, thực hiện chia lưới,
     * hoán đổi Fisher-Yates, vẽ ảnh xáo trộn và trả về ScrambleResult chứa byte[] ảnh đã xáo trộn.
     */
    public ScrambleResult processAndScrambleUrl(String imageUrl, int rows, int cols) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }
        try {
            BufferedImage sourceImage;
            if (imageUrl.startsWith("data:image")) {
                String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
                byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
                sourceImage = ImageIO.read(new ByteArrayInputStream(decodedBytes));
            } else {
                java.net.URL url = new java.net.URI(imageUrl).toURL();
                sourceImage = ImageIO.read(url);
            }

            if (sourceImage == null) {
                log.warn("Không thể đọc dữ liệu ảnh từ URL: {}", imageUrl);
                return null;
            }
            return processAndScramble(sourceImage, rows, cols);
        } catch (Exception e) {
            log.error("Lỗi khi xáo trộn ảnh từ URL {}: {}", imageUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Thuật toán Fisher-Yates Shuffle
     */
    private void fisherYatesShuffle(int[] array) {
        Random rand = new SecureRandom();
        for (int i = array.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    /**
     * Mã hóa mảng số nguyên mapping thành chuỗi AES-128-CBC + 16-byte random IV + Base64
     */
    public String encryptMapping(int[] mapping, String key) {
        try {
            String jsonArrayStr = Arrays.toString(mapping).replaceAll("\\s+", "");

            byte[] keyBytes = new byte[16];
            byte[] inputKeyBytes = (key != null ? key : secretKey).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(inputKeyBytes, 0, keyBytes, 0, Math.min(inputKeyBytes.length, 16));

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] encryptedBytes = cipher.doFinal(jsonArrayStr.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Lỗi khi mã hóa mapping: {}", e.getMessage(), e);
            throw new RuntimeException("Encryption mapping error", e);
        }
    }

    /**
     * Giải mã chuỗi AES Base64 trở lại mảng số nguyên mapping
     */
    public int[] decryptMapping(String encryptedBase64, String key) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[combined.length - 16];

            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);

            byte[] keyBytes = new byte[16];
            byte[] inputKeyBytes = (key != null ? key : secretKey).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(inputKeyBytes, 0, keyBytes, 0, Math.min(inputKeyBytes.length, 16));

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String jsonStr = new String(decryptedBytes, StandardCharsets.UTF_8);

            String content = jsonStr.substring(1, jsonStr.length() - 1);
            if (content.trim().isEmpty()) {
                return new int[0];
            }
            String[] tokens = content.split(",");
            int[] result = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                result[i] = Integer.parseInt(tokens[i].trim());
            }
            return result;
        } catch (Exception e) {
            log.error("Lỗi khi giải mã mapping: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption mapping error", e);
        }
    }

    /**
     * Chuyển BufferedImage sang byte array với định dạng JPG/PNG
     */
    public byte[] convertToByteArray(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, formatName != null ? formatName : "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * API Service: Tạo mới hoặc sinh lại danh sách Scrambled Pages cho 1 chapter bất kỳ theo chapterId.
     * Tải các ảnh gốc hiện có của chapter, xáo trộn các mảnh ghép (rows x cols), upload ảnh ĐÃ XÁO TRỘN lên Cloudinary,
     * mã hóa mảng thứ tự AES-128 và lưu mảng scrambledPages vào ChapterEntity (PostgreSQL JSONB).
     */
    @org.springframework.transaction.annotation.Transactional
    public java.util.List<com.sep.comiverse.dto.response.PageResponseDto> generateScrambledPagesForChapter(
            java.util.UUID chapterId,
            int rows,
            int cols,
            boolean forceRegenerate
    ) {
        com.sep.comiverse.entity.ChapterEntity chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new com.sep.comiverse.exception.CustomException(
                        404,
                        "Chapter not found with id: " + chapterId,
                        org.springframework.http.HttpStatus.NOT_FOUND
                ));

        if (!forceRegenerate && chapter.getScrambledPages() != null && !chapter.getScrambledPages().isEmpty()) {
            return mapScrambledPagesToDto(chapter.getScrambledPages());
        }

        java.util.List<String> originalImages = chapter.getImages();
        if (originalImages == null || originalImages.isEmpty()) {
            throw new com.sep.comiverse.exception.CustomException(
                    400,
                    "Chapter has no images to scramble",
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        if (rows <= 0) rows = 4;
        if (cols <= 0) cols = 4;

        java.util.UUID comicId = chapter.getComic() != null ? chapter.getComic().getId() : java.util.UUID.randomUUID();
        String chapterNumber = chapter.getChapterNumber() != null ? chapter.getChapterNumber() : "1";
        String targetFolder = "comiverse/chapters/" + comicId + "/chapter-" + chapterNumber + "/scrambled";

        java.util.List<com.sep.comiverse.entity.ChapterEntity.ScrambledPageData> scrambledPages = new java.util.ArrayList<>();

        for (int i = 0; i < originalImages.size(); i++) {
            String originalUrl = originalImages.get(i);
            int pageNum = i + 1;

            byte[] scrambledBytes = null;
            String encryptedMapping = "";

            ScrambleResult result = processAndScrambleUrl(originalUrl, rows, cols);
            if (result != null) {
                encryptedMapping = result.getEncryptedMapping();
                scrambledBytes = result.getScrambledBytes();
            }

            String scrambledImageUrl = originalUrl;
            if (scrambledBytes != null && scrambledBytes.length > 0 && cloudinaryStorageService != null) {
                try {
                    CloudinaryUploadResult upload = cloudinaryStorageService.uploadImage(
                            scrambledBytes,
                            "scrambled_page_" + pageNum,
                            targetFolder
                    );
                    if (upload != null && upload.getSecureUrl() != null) {
                        scrambledImageUrl = upload.getSecureUrl();
                    }
                } catch (Exception e) {
                    log.error("Lỗi khi upload scrambled image cho page {} của chapter {}: {}", pageNum, chapterId, e.getMessage());
                }
            }

            scrambledPages.add(com.sep.comiverse.entity.ChapterEntity.ScrambledPageData.builder()
                    .pageNumber(pageNum)
                    .scrambledImageUrl(scrambledImageUrl)
                    .encryptedMapping(encryptedMapping)
                    .rows(rows)
                    .cols(cols)
                    .build());
        }

        chapter.setScrambledPages(scrambledPages);
        chapterRepository.save(chapter);

        return mapScrambledPagesToDto(scrambledPages);
    }

    private java.util.List<com.sep.comiverse.dto.response.PageResponseDto> mapScrambledPagesToDto(
            java.util.List<com.sep.comiverse.entity.ChapterEntity.ScrambledPageData> scrambledPages
    ) {
        java.util.List<com.sep.comiverse.dto.response.PageResponseDto> dtos = new java.util.ArrayList<>();
        if (scrambledPages == null) return dtos;
        for (com.sep.comiverse.entity.ChapterEntity.ScrambledPageData data : scrambledPages) {
            dtos.add(com.sep.comiverse.dto.response.PageResponseDto.builder()
                    .pageNumber(data.getPageNumber() != null ? data.getPageNumber() : 1)
                    .scrambledImageUrl(data.getScrambledImageUrl())
                    .cols(data.getCols() != null ? data.getCols() : 4)
                    .rows(data.getRows() != null ? data.getRows() : 4)
                    .encryptedMapping(data.getEncryptedMapping())
                    .token(java.util.UUID.randomUUID().toString())
                    .build());
        }
        return dtos;
    }

}
