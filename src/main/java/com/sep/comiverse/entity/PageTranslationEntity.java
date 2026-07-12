package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.PageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "page_translation")
public class PageTranslationEntity extends BaseEntity{
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TeamTaskEntity taskId;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private PageStatus status = PageStatus.BACKLOG;

    protected void ensureDefaults() {
        if (status == null) {
            status = PageStatus.BACKLOG;
        }
    }
}
