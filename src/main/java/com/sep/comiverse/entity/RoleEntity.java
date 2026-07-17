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
@Table(name = "roles")
@EqualsAndHashCode(callSuper = true)
public class RoleEntity extends BaseEntity {

    @Column(name = "role_name", nullable = false, unique = true)
    private String roleName; // ADMIN, MODERATOR, AUTHOR, READER, TRANSLATOR, PROJECT_LEADER
}
