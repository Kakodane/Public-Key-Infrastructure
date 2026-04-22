package com.ib.service;

import com.ib.domain.entity.User;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.LoginRequest;
import com.ib.dto.auth.RefreshTokenRequest;
import com.ib.dto.auth.TokenResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final AuthenticationManager authenticationManager;
	private final UserRepository userRepository;
	private final TokenService tokenService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
			AuthenticationManager authenticationManager,
			UserRepository userRepository,
			TokenService tokenService,
			RefreshTokenService refreshTokenService
	) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
		this.tokenService = tokenService;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		String email = request.email().trim();
		try {
			Authentication auth = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(email, request.password()));
			User user = userRepository.findByEmailIgnoreCase(auth.getName())
					.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
			return buildTokenResponse(user);
		}
		catch (BadCredentialsException ex) {
			throw new BadCredentialsException("Invalid credentials", ex);
		}
		catch (LockedException ex) {
			throw ex;
		}
		catch (AuthenticationException ex) {
			throw new BadCredentialsException("Invalid credentials", ex);
		}
	}

	@Transactional
	public TokenResponse refresh(RefreshTokenRequest request) {
		User user = refreshTokenService.validateAndConsumeForRotation(request.refreshToken())
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
		User fresh = userRepository.findById(user.getId())
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
		if (!fresh.isEnabled() || !fresh.isEmailVerified()) {
			throw new BadCredentialsException("Invalid refresh token");
		}
		return buildTokenResponse(fresh);
	}

	private TokenResponse buildTokenResponse(User user) {
		String access = tokenService.createAccessToken(user);
		var issued = refreshTokenService.issueForUser(user);
		return new TokenResponse(
				access,
				tokenService.accessTokenExpiresInSeconds(),
				issued.rawToken(),
				issued.refreshExpiresInSeconds()
		);
	}
}
