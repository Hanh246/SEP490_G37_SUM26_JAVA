package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comics")
@EqualsAndHashCode(callSuper = true)
public class ComicEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "project_team")
    private String projectTeam;

    @Column(name = "chapters")
    private Integer chapters;

    @Column(name = "views")
    private String views;

    @Column(name = "status")
    private String status; // Ongoing, Completed, Paused, Archived

    @Column(name = "genres")
    private String genres; // Comma-separated list of genres (e.g. "Action, Fantasy")

    @Column(name = "cover")
    private String cover; // Cover emoji or image path (e.g. "⚔️")
}
