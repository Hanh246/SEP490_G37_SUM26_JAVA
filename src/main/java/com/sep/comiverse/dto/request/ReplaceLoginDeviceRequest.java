package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ReplaceLoginDeviceRequest {
    private UUID challengeId;
    private UUID deviceToRemoveId;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "OTP must contain 6 digits")
    private String otp;
}
