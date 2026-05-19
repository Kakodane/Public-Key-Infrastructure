package com.ib.dto.auth;

public record TwoFactorSetupResponse(
		String secret,
		String qrCodeUri
) {
}
