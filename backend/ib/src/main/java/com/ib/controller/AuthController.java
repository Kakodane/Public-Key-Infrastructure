package com.ib.controller;

import com.ib.dto.auth.LoginRequest;
import com.ib.dto.auth.RefreshTokenRequest;
import com.ib.dto.auth.TokenResponse;
import com.ib.security.AuthenticatedUser;
import com.ib.service.AuthService;
import com.ib.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenService refreshTokenService;

	public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		try {
			return authService.login(request);
		}
		catch (BadCredentialsException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		catch (DisabledException ex) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account disabled");
		}
		catch (LockedException ex) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account locked or email not verified");
		}
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		try {
			return authService.refresh(request);
		}
		catch (BadCredentialsException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
		}
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal AuthenticatedUser user) {
		refreshTokenService.revokeAllForUser(user.id());
	}
}
