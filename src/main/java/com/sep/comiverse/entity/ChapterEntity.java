package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters")
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "projectTeam")
public class ChapterEntity extends BaseEntity {

    @Column(name = "num")
    private String num; // e.g. "Chapter 45"

    @Column(name = "date")
    private String date; // date/time representation or formatted relative label

    @Column(name = "words")
    private Integer words;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_team_id")
    private ProjectTeamEntity projectTeam;
}
