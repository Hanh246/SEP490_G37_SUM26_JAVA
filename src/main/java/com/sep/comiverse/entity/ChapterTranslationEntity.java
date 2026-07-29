package com.sep.comiverse.entity;

import com.sep.comiverse.entity.BaseEntity;
import com.sep.comiverse.entity.ChapterEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_team_id", insertable = false, updatable = false)
    private ProjectTeamEntity projectTeam;
}