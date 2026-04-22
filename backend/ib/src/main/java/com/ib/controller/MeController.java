package com.ib.controller;

import com.ib.dto.auth.MeResponse;
import com.ib.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		String role = user.getAuthorities().stream()
				.findFirst()
				.map(a -> a.getAuthority())
				.orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
		return new MeResponse(user.id(), user.email(), role);
	}
}
