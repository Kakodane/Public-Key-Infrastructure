package com.ib.security;

import com.ib.domain.repository.UserRepository;
import com.ib.service.TokenService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DatabaseUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return userRepository.findByEmailIgnoreCase(username)
				.map(user -> org.springframework.security.core.userdetails.User.builder()
						.username(user.getEmail())
						.password(user.getPasswordHash())
						.authorities(TokenService.toAuthority(user.getRole()))
						.disabled(!user.isEnabled())
						.accountLocked(!user.isEmailVerified())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
