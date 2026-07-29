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
@Table(name = "banned_keywords")
@EqualsAndHashCode(callSuper = true)
public class BannedKeywordEntity extends BaseEntity {

    @Column(name = "word", unique = true)
    private String word;

    @Column(name = "category")
    private String category;

    @Column(name = "severity")
    private String severity;
}
