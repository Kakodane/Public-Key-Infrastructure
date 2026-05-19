package com.ib.dto.auth;


public record ActivationChallengeResponse(
        String email,
        String publicKeyPem,
        String encryptedChallengeBase64
) {
}
