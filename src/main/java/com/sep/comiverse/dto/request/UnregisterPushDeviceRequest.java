package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnregisterPushDeviceRequest {

    @NotBlank
    @Size(max = 4096)
    private String token;
}
