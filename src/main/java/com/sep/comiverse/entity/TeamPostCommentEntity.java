package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "team_post_comments")
public class TeamPostCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Column(name = "author")
    private String author;

    @Column(name = "role")
    private String role;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "time")
    private String time;

    @Column(name = "parent_id")
    private UUID parentId;

    @Builder.Default
    @Column(name = "likes")
    private Integer likes = 0;

    @Column(name = "liked_by_users", columnDefinition = "TEXT")
    private String likedByUsers;
}
