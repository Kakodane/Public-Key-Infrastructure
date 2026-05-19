package com.ib.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorCodeRequest(
		@NotBlank
		@Pattern(regexp = "\\d{6}", message = "Code must be a 6-digit number")
		String code
) {
}
