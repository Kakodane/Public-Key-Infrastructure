package com.ib.service;

import com.ib.domain.entity.User;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.LoginRequest;
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

	public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
	}

	@Transactional
	public User authenticate(LoginRequest request) {
		String email = request.email().trim();
		try {
			Authentication auth = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(email, request.password()));
			return userRepository.findByEmailIgnoreCase(auth.getName())
					.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
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

	public void requireEnabledUser(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
		if (!user.isEnabled() || !user.isEmailVerified()) {
			throw new BadCredentialsException("Account disabled");
		}
	}
}
