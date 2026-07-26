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
@Table(name = "user_ratings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "comic_id"})
})
@EqualsAndHashCode(callSuper = true)
public class UserRatingEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "score", nullable = false)
    private Integer score;
}
