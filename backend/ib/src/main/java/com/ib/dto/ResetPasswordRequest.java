package com.ib.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank String decryptedChallengeBase64,

        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Password must contain at least one lowercase, one uppercase, one digit and one special character"
        )
        String newPassword,

        @NotBlank String confirmPassword
) {
}