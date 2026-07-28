package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "glossary_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlossaryTermEntity extends BaseEntity {

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "source_term", nullable = false)
    private String source;

    @Column(name = "target_term", nullable = false)
    private String target;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;
}