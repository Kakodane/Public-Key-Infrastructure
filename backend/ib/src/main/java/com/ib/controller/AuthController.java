package com.ib.controller;

import com.ib.domain.entity.User;
import com.ib.dto.auth.LoginRequest;
import com.ib.dto.auth.TokenResponse;
import com.ib.security.AuthenticatedUser;
import com.ib.service.AuthService;
import com.ib.service.RefreshTokenService;
import com.ib.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String REFRESH_COOKIE = "refreshToken";

	private final AuthService authService;
	private final RefreshTokenService refreshTokenService;
	private final TokenService tokenService;

	public AuthController(AuthService authService, RefreshTokenService refreshTokenService, TokenService tokenService) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
		this.tokenService = tokenService;
	}

	@PostMapping("/login")
	public TokenResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletResponse response
	) {
		try {
			User user = authService.authenticate(request);
			String access = tokenService.createAccessToken(user);
			var issued = refreshTokenService.issueForUser(user);
			response.addCookie(buildRefreshCookie(issued.rawToken(), issued.refreshExpiresInSeconds()));
			return new TokenResponse(access, tokenService.accessTokenExpiresInSeconds());
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
	public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		String raw = readRefreshCookie(request);
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
		}
		try {
			User user = refreshTokenService.validateAndConsumeForRotation(raw)
					.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
			authService.requireEnabledUser(user.getId());
			String access = tokenService.createAccessToken(user);
			var issued = refreshTokenService.issueForUser(user);
			response.addCookie(buildRefreshCookie(issued.rawToken(), issued.refreshExpiresInSeconds()));
			return new TokenResponse(access, tokenService.accessTokenExpiresInSeconds());
		}
		catch (BadCredentialsException ex) {
			response.addCookie(clearRefreshCookie());
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
		}
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal AuthenticatedUser user, HttpServletResponse response) {
		if (user != null) {
			refreshTokenService.revokeAllForUser(user.id());
		}
		response.addCookie(clearRefreshCookie());
	}

	private Cookie buildRefreshCookie(String token, long maxAgeSeconds) {
		Cookie cookie = new Cookie(REFRESH_COOKIE, token);
		cookie.setHttpOnly(true);
		cookie.setSecure(false); // set true in production (HTTPS)
		cookie.setPath("/api/auth");
		cookie.setMaxAge((int) maxAgeSeconds);
		cookie.setAttribute("SameSite", "Strict");
		return cookie;
	}

	private Cookie clearRefreshCookie() {
		Cookie cookie = new Cookie(REFRESH_COOKIE, "");
		cookie.setHttpOnly(true);
		cookie.setSecure(false);
		cookie.setPath("/api/auth");
		cookie.setMaxAge(0);
		cookie.setAttribute("SameSite", "Strict");
		return cookie;
	}

	private String readRefreshCookie(HttpServletRequest request) {
		if (request.getCookies() == null) return null;
		return Arrays.stream(request.getCookies())
				.filter(c -> REFRESH_COOKIE.equals(c.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}
}
