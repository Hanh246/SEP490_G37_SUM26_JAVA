package com.sep.comiverse.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslatorRegistrationRequest {
    private List<String> specializations;
    private Integer experiencedYears;
    private String phone;
    private String facebookUrl;
    private String cvUrl;
    private String bio;
}
