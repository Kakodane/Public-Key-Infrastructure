package com.ib.dto;


public record PasswordResetChallengeResponse(
        String email,
        String publicKeyPem,
        String encryptedChallengeBase64
) {
}
