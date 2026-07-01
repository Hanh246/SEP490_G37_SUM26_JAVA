package com.sep.comiverse.entity;

import com.sep.comiverse.entity.id.UuidVersion7Generator;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.domain.Sort;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@MappedSuperclass
public class BaseEntity implements Serializable {
    public static Sort getDefaultSorting() {
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    @Id
    @GeneratedValue(generator = "uuid-v7")
    @GenericGenerator(name = "uuid-v7", type = UuidVersion7Generator.class)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "deleted")
    private Boolean deleted = false;

    @Column(name = "create_at", nullable = false)
    private Instant createdAt;

    @Column(name = "update_at")
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null)
            createdAt = Instant.now();
        if (this.updatedAt == null)
            updatedAt = Instant.now();
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @PreRemove
    protected void preRemove() {
        this.updatedAt = Instant.now();
    }

}
