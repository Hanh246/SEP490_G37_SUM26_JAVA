package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reading_histories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "chapter_id"})
})
@EqualsAndHashCode(callSuper = true)
public class ReadingHistoryEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;
}
