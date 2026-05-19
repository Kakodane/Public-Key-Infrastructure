package com.ib.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountRequest(
        @NotBlank String token,
        @NotBlank String decryptedChallengeBase64
) {
}
