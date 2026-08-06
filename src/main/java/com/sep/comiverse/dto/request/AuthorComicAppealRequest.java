package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicAppealRequest {

    private String category;

    @NotBlank(message = "Appeal statement cannot be blank")
    @Size(min = 10, max = 2000, message = "Appeal statement must be between 10 and 2000 characters")
    private String reason;
}
