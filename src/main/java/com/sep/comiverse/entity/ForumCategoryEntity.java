package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "forum_categories", indexes = {
        @Index(name = "idx_forum_categories_name", columnList = "name"),
        @Index(name = "idx_forum_categories_active", columnList = "is_active, deleted")
})
@EqualsAndHashCode(callSuper = true)
public class ForumCategoryEntity extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "color", nullable = false, length = 7)
    @Builder.Default
    private String color = "#a855f7";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
