package com.ib.dto.auth;

public record MeResponse(
		Long id,
		String email,
		String role,
		Long organizationId,
		String organizationName
) {
}
