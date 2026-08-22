package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token cannot be blank")
    @Size(max = 4096, message = "Refresh token is too long")
    private String refreshToken;
}
