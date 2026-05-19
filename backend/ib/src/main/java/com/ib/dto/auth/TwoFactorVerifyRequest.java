package com.ib.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
		@NotBlank String twoFactorToken,
		@NotBlank String code
) {
}
