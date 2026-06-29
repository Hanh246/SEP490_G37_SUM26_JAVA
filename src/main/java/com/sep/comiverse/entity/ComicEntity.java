package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comics")
@EqualsAndHashCode(callSuper = true)
public class ComicEntity extends BaseEntity {

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug")
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "author")
    private String author;

    @Column(name = "project_team")
    private String projectTeam;

    @Column(name = "chapters")
    private Integer chapters;

    @Column(name = "views")
    private String views;

    @Column(name = "status")
    private String status; // Publication status: Ongoing, Completed, Hiatus, Archived

    @Column(name = "moderation_status")
    private String moderationStatus; // DRAFT, SUBMITTED_FOR_REVIEW, PUBLISHED, REJECTED, NEEDS_CHANGES

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    private String moderationNote;

    @Column(name = "published_at")
    private Date publishedAt;

    @Column(name = "genres")
    private String genres; // Comma-separated list of genres (e.g. "Action, Fantasy")

    @Column(name = "cover")
    private String cover; // Cover emoji or image path fallback
}
