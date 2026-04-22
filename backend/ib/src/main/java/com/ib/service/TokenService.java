package com.ib.service;

import com.ib.config.JwtProperties;
import com.ib.domain.entity.User;
import com.ib.domain.enums.Role;
import com.ib.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class TokenService {

	public static final String CLAIM_TYP = "typ";
	public static final String CLAIM_ROLE = "role";
	public static final String TYP_ACCESS = "access";

	private final JwtProperties jwtProperties;
	private final SecretKey signingKey;

	public TokenService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
	}

	public String createAccessToken(User user) {
		var now = new Date();
		var exp = new Date(now.getTime() + jwtProperties.accessTtl().toMillis());
		String roleAuthority = toAuthority(user.getRole());
		return Jwts.builder()
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim(CLAIM_TYP, TYP_ACCESS)
				.claim(CLAIM_ROLE, roleAuthority)
				.issuedAt(now)
				.expiration(exp)
				.signWith(signingKey)
				.compact();
	}

	public Authentication parseAuthentication(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		if (!TYP_ACCESS.equals(claims.get(CLAIM_TYP, String.class))) {
			throw new JwtException("Invalid token type");
		}
		Long userId = Long.parseLong(claims.getSubject());
		String email = claims.get("email", String.class);
		String roleAuthority = claims.get(CLAIM_ROLE, String.class);
		if (roleAuthority == null || roleAuthority.isBlank()) {
			throw new JwtException("Missing role");
		}
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleAuthority));
		AuthenticatedUser principal = new AuthenticatedUser(userId, email, authorities);
		return new UsernamePasswordAuthenticationToken(principal, null, authorities);
	}

	public long accessTokenExpiresInSeconds() {
		return jwtProperties.accessTtl().toSeconds();
	}

	public static String toAuthority(Role role) {
		return switch (role) {
			case ADMIN -> "ROLE_ADMIN";
			case CA_USER -> "ROLE_CA_USER";
			case REGULAR_USER -> "ROLE_REGULAR_USER";
		};
	}
}
