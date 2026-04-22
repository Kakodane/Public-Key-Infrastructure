package com.ib.service;

import com.ib.config.JwtProperties;
import com.ib.domain.entity.RefreshToken;
import com.ib.domain.entity.User;
import com.ib.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int RAW_TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	public record IssuedRefreshToken(String rawToken, long refreshExpiresInSeconds) {
	}

	@Transactional
	public IssuedRefreshToken issueForUser(User user) {
		byte[] raw = new byte[RAW_TOKEN_BYTES];
		RANDOM.nextBytes(raw);
		String rawToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String hash = sha256Hex(rawToken);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.refreshTtl());
		RefreshToken entity = RefreshToken.builder()
				.user(user)
				.tokenHash(hash)
				.expiresAt(expiresAt)
				.revoked(false)
				.createdAt(now)
				.build();
		refreshTokenRepository.save(entity);
		return new IssuedRefreshToken(rawToken, jwtProperties.refreshTtl().toSeconds());
	}

	@Transactional
	public Optional<User> validateAndConsumeForRotation(String rawToken) {
		String hash = sha256Hex(rawToken);
		Optional<RefreshToken> row = refreshTokenRepository.findByTokenHash(hash);
		if (row.isEmpty()) {
			return Optional.empty();
		}
		RefreshToken token = row.get();
		Instant now = Instant.now();
		if (token.isRevoked() || !token.getExpiresAt().isAfter(now)) {
			return Optional.empty();
		}
		token.setRevoked(true);
		refreshTokenRepository.save(token);
		return Optional.of(token.getUser());
	}

	@Transactional
	public void revokeAllForUser(Long userId) {
		List<RefreshToken> active = refreshTokenRepository.findAllByUser_IdAndRevokedIsFalse(userId);
		for (RefreshToken t : active) {
			t.setRevoked(true);
		}
		refreshTokenRepository.saveAll(active);
	}

	public long refreshExpiresInSeconds() {
		return jwtProperties.refreshTtl().toSeconds();
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
