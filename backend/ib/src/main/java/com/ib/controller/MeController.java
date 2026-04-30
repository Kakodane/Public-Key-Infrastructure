package com.ib.controller;

import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.MeResponse;
import com.ib.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

	private final UserRepository userRepository;

	@GetMapping("/me")
	@Transactional(readOnly = true)
	public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		String role = user.getAuthorities().stream()
				.findFirst()
				.map(a -> a.getAuthority())
				.orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));

		User dbUser = userRepository.findByEmailIgnoreCase(user.email())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		Organization org = dbUser.getOrganization();
		return new MeResponse(
				user.id(),
				user.email(),
				role,
				org != null ? org.getId() : null,
				org != null ? org.getName() : null
		);
	}
}
