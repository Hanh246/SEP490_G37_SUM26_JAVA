package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.AuthorType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "authors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_author_user", columnNames = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class AuthorEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_author_user")
    )
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 40)
    @Builder.Default
    private AuthorType authorType = AuthorType.INDIVIDUAL;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "language", length = 100)
    private String language;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "external_profile_ref", length = 100)
    private String externalProfileRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private UserEntity createdByAdmin;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
