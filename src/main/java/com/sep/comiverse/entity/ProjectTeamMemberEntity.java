package com.sep.comiverse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Data
@Entity
@Table(name = "project_team_members")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"team", "user"})
public class ProjectTeamMemberEntity extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "team_id", nullable = false)
    private ProjectTeamEntity team;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

}
