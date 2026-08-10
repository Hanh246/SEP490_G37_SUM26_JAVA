package com.sep.comiverse.entity;

import com.sep.comiverse.entity.BaseEntity;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.enums.ChapterTranslationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;


@Entity
@Table(
        name = "chapter_translations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id", "language_code"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterTranslationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private ChapterEntity chapter;

    @Column(name = "language_code")
    private String languageCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pages_bubbles", columnDefinition = "jsonb")
    private String pagesBubbles;

    @Column(name = "project_team_id")
    private UUID projectTeamId;    // team nào đã dịch bản này

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private ChapterTranslationStatus status = ChapterTranslationStatus.PUBLISHED;

    @PrePersist
    protected void ensureTranslationDefaults() {
        if (this.status == null) {
            this.status = ChapterTranslationStatus.PUBLISHED;
        }
    }
}