package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi thông tin trang truyện được bảo vệ bằng kỹ thuật xáo trộn mảnh (Image Slicing).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDto {

    /**
     * Số trang trong chapter (bắt đầu từ 1)
     */
    private int pageNumber;

    /**
     * URL ảnh đã bị xáo trộn lưu thực tế trên CDN Cloudinary
     */
    private String scrambledImageUrl;

    /**
     * Số cột mảnh ghép (Mặc định 4)
     */
    private int cols;

    /**
     * Số hàng mảnh ghép (Mặc định 4)
     */
    private int rows;

    /**
     * Chuỗi mã hóa AES đại diện cho mảng vị trí xáo trộn (Mapping Array)
     */
    private String encryptedMapping;

    /**
     * Access token / temporary token xác thực (nếu có)
     */
    private String token;
}
