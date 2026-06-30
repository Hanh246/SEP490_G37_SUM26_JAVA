package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "team_messages")
public class TeamMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "sender")
    private String sender;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "time")
    private String time;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;
}
