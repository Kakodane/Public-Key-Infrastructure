package com.ib.controller;

import com.ib.crypto.TotpSecretCipher;
import com.ib.domain.entity.User;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.LoginRequest;
import com.ib.dto.auth.LoginResponse;
import com.ib.dto.auth.TokenResponse;
import com.ib.dto.auth.TwoFactorVerifyRequest;
import com.ib.security.AuthenticatedUser;
import com.ib.service.AuthService;
import com.ib.service.RefreshTokenService;
import com.ib.service.TokenService;
import com.ib.service.TotpService;
import io.jsonwebtoken.JwtException;
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
	private final TotpService totpService;
	private final TotpSecretCipher totpSecretCipher;
	private final UserRepository userRepository;

	public AuthController(
			AuthService authService,
			RefreshTokenService refreshTokenService,
			TokenService tokenService,
			TotpService totpService,
			TotpSecretCipher totpSecretCipher,
			UserRepository userRepository
	) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
		this.tokenService = tokenService;
		this.totpService = totpService;
		this.totpSecretCipher = totpSecretCipher;
		this.userRepository = userRepository;
	}

	@PostMapping("/login")
	public LoginResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletResponse response
	) {
		try {
			User user = authService.authenticate(request);
			if (user.isTwoFactorEnabled()) {
				String challengeToken = tokenService.createTwoFactorChallengeToken(user);
				return new LoginResponse(true, challengeToken, null, null);
			}
			return completeLogin(user, response);
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

	@PostMapping("/2fa/verify")
	public LoginResponse verifyTwoFactor(
			@Valid @RequestBody TwoFactorVerifyRequest request,
			HttpServletResponse response
	) {
		try {
			Long userId = tokenService.parseTwoFactorChallengeToken(request.twoFactorToken());
			User user = userRepository.findById(userId)
					.orElseThrow(() -> new BadCredentialsException("Invalid token"));
			String secret = totpSecretCipher.decrypt(user);
			if (!user.isTwoFactorEnabled() || secret == null) {
				throw new BadCredentialsException("Invalid token");
			}
			if (!totpService.verifyCode(secret, request.code())) {
				throw new BadCredentialsException("Invalid code");
			}
			authService.requireEnabledUser(user.getId());
			return completeLogin(user, response);
		}
		catch (JwtException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
		}
		catch (BadCredentialsException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
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

	private LoginResponse completeLogin(User user, HttpServletResponse response) {
		String access = tokenService.createAccessToken(user);
		var issued = refreshTokenService.issueForUser(user);
		response.addCookie(buildRefreshCookie(issued.rawToken(), issued.refreshExpiresInSeconds()));
		return new LoginResponse(false, null, access, tokenService.accessTokenExpiresInSeconds());
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
