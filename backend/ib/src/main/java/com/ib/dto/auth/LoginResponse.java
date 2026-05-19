package com.ib.dto.auth;

public record LoginResponse(
		boolean twoFactorRequired,
		String twoFactorToken,
		String accessToken,
		Long expiresIn
) {
}
