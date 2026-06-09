package com.ib.bootstrap;

import com.ib.crypto.OrganizationKeyService;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.enums.Role;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo users (checkpoint: direct DB inserts). Password for all: {@value #DEMO_PASSWORD}.
 */
@Component
@Order(10)
public class AuthDevDataLoader implements CommandLineRunner {

	static final String DEMO_PASSWORD = "ChangeMe1!";

	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordEncoder passwordEncoder;
	private final OrganizationKeyService organizationKeyService;

	public AuthDevDataLoader(
			UserRepository userRepository,
			OrganizationRepository organizationRepository,
			PasswordEncoder passwordEncoder,
			OrganizationKeyService organizationKeyService
	) {
		this.userRepository = userRepository;
		this.organizationRepository = organizationRepository;
		this.passwordEncoder = passwordEncoder;
		this.organizationKeyService = organizationKeyService;
	}

	@Override
	public void run(String... args) {
		if (userRepository.count() > 0) {
			return;
		}
		Organization org = organizationRepository.save(Organization.builder()
				.name("Demo Organization")
				.build());
		organizationKeyService.getOrCreateOrgKey(org);

		String hash = passwordEncoder.encode(DEMO_PASSWORD);

		userRepository.save(User.builder()
				.email("admin@ib.local")
				.passwordHash(hash)
				.role(Role.ADMIN)
				.organization(null)
				.enabled(true)
				.emailVerified(true)
				.firstName("Admin")
				.lastName("User")
				.build());

		userRepository.save(User.builder()
				.email("ca@ib.local")
				.passwordHash(hash)
				.role(Role.CA_USER)
				.organization(org)
				.enabled(true)
				.emailVerified(true)
				.firstName("CA")
				.lastName("User")
				.build());

		userRepository.save(User.builder()
				.email("user@ib.local")
				.passwordHash(hash)
				.role(Role.REGULAR_USER)
				.organization(org)
				.enabled(true)
				.emailVerified(true)
				.firstName("Regular")
				.lastName("User")
				.build());

		userRepository.save(User.builder()
				.email("user2@ib.local")
				.passwordHash(hash)
				.role(Role.REGULAR_USER)
				.organization(org)
				.enabled(true)
				.emailVerified(true)
				.firstName("Regular")
				.lastName("User Two")
				.build());
		userRepository.save(User.builder()
				.email("user3@ib.local")
				.passwordHash(hash)
				.role(Role.REGULAR_USER)
				.organization(org)
				.enabled(true)
				.emailVerified(true)
				.firstName("Regular")
				.lastName("User Three")
				.build());
	}
}
