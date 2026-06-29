package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "chapter_pages",
        indexes = {
                @Index(name = "idx_chapter_pages_chapter", columnList = "chapter_id"),
                @Index(name = "idx_chapter_pages_comic_page", columnList = "comic_id, page_number")
        }
)
@EqualsAndHashCode(callSuper = true)
public class ChapterPageEntity extends BaseEntity {

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;
}
