package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "genre")
@EqualsAndHashCode(callSuper = true)
public class GenreEntity extends BaseEntity{
    @Column(name = "name")
    private String name;

    @Column(name = "slug")
    private String slug;
}
