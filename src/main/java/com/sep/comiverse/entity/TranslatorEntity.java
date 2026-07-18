package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "translator")
public class TranslatorEntity extends BaseEntity{

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "specializations", columnDefinition = "text[]", nullable = false)
    private List<String> specializations = new ArrayList<>();

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears = 0;

    @Column(name = "phone", columnDefinition = "text")
    private String phoneNumber;

    @Column(name = "facebook")
    private String facebookUrl;

    @Column(name = "joinedProjectCount", nullable = false)
    private Integer joinedProjectCount = 0;

}
