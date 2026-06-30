package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"comic_id", "chapter_number"})
})
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "comic")
public class ChapterEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comic_id", nullable = false)
    private ComicEntity comic;

    @Column(name = "chapter_number", nullable = false)
    private String chapterNumber;

    @Column(name = "title")
    private String title;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "images", columnDefinition = "text[]", nullable = false)
    private List<String> images;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "is_premium", nullable = false)
    private Boolean isPremium = false;
}
