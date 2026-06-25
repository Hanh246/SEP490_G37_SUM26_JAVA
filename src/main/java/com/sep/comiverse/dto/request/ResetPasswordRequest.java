package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP code cannot be blank")
    private String otp;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, message = "New password must have at least 6 characters")
    private String newPassword;
}
