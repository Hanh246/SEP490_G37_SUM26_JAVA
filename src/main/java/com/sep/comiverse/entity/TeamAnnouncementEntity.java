package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "team_announcements")
public class TeamAnnouncementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "author")
    private String author;

    @Column(name = "role")
    private String role;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "time")
    private String time;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "likes")
    private Integer likes;

    @Builder.Default
    @Column(name = "is_pinned", nullable = false, columnDefinition = "boolean default false")
    private Boolean isPinned = false;

    @Builder.Default
    @Column(name = "is_edited", nullable = false, columnDefinition = "boolean default false")
    private Boolean isEdited = false;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "liked_by_users", columnDefinition = "TEXT")
    private String likedByUsers;
}
