package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    @Size(min = 16, max = 128)
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "Invalid device identifier")
    private String deviceId;

    @Size(max = 120)
    private String deviceName;

    @Pattern(regexp = "(?i)android|ios", message = "platform must be android or ios")
    private String platform;
}
