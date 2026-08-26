package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._]+$", message = "Username can only contain letters, numbers, dots or underscores")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Full name cannot be blank")
    @Pattern(regexp = "^[\\p{L}\\s'-]{2,50}$",
            message = "Full name must be 2-50 letters and may include spaces, apostrophes or hyphens")
    private String fullName;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$",
            message = "Invalid phone number format")
    private String phone;

    private String role;
    private LocalDate dateOfBirth;

    private java.util.List<String> assignedLanguages;
}
