package com.ib.dto.auth;

public record TokenResponse(
		String accessToken,
		long expiresIn,
		String refreshToken,
		long refreshExpiresIn
) {
}
