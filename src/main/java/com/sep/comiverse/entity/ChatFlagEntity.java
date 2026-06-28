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
@Table(name = "chat_flags")
@EqualsAndHashCode(callSuper = true)
public class ChatFlagEntity extends BaseEntity {

    @Column(name = "username")
    private String user;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status")
    private String status; // flagged, warned
}
