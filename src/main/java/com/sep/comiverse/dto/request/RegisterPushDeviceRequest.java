package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPushDeviceRequest {

    @NotBlank
    @Size(max = 4096)
    private String token;

    @NotBlank
    @Pattern(regexp = "(?i)android|ios", message = "platform must be android or ios")
    private String platform;

    @Size(max = 120)
    private String deviceName;
}
