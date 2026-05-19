package com.ib.dto.auth;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Password must contain at least one lowercase, one uppercase, one digit and one special character"
        )
        String password,

        @NotBlank String confirmPassword,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String organizationName,

        @NotBlank String publicKeyPem
) {
}
