package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comic_daily_views", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"comic_id", "log_date"})
})
@EqualsAndHashCode(callSuper = true)
public class ComicDailyVewsEntity extends BaseEntity {

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
