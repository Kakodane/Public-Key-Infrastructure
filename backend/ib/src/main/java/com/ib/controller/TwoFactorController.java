package com.ib.controller;

import com.ib.crypto.TotpSecretCipher;
import com.ib.domain.entity.User;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.TwoFactorCodeRequest;
import com.ib.dto.auth.TwoFactorSetupResponse;
import com.ib.dto.auth.TwoFactorStatusResponse;
import com.ib.security.AuthenticatedUser;
import com.ib.service.TotpService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

	private final UserRepository userRepository;
	private final TotpService totpService;
	private final TotpSecretCipher totpSecretCipher;

	@PostMapping("/setup")
	@PreAuthorize("hasRole('REGULAR_USER')")
	@Transactional
	public TwoFactorSetupResponse setup(@AuthenticationPrincipal AuthenticatedUser principal) {
		User user = loadUser(principal);
		if (user.isTwoFactorEnabled()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is already enabled");
		}
		String secret = totpService.generateSecret();
		totpSecretCipher.encryptAndStore(user, secret);
		userRepository.save(user);
		String qrCodeUri = totpService.generateQrCodeUri(secret, user.getEmail());
		return new TwoFactorSetupResponse(secret, qrCodeUri);
	}

	@PostMapping("/confirm")
	@PreAuthorize("hasRole('REGULAR_USER')")
	@Transactional
	@ResponseStatus(HttpStatus.OK)
	public void confirm(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody TwoFactorCodeRequest request
	) {
		User user = loadUser(principal);
		if (user.isTwoFactorEnabled()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is already enabled");
		}
		String secret = totpSecretCipher.decrypt(user);
		if (secret == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor setup has not been started");
		}
		if (!totpService.verifyCode(secret, request.code())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid verification code");
		}
		user.setTwoFactorEnabled(true);
		userRepository.save(user);
	}

	@PostMapping("/disable")
	@PreAuthorize("hasRole('REGULAR_USER')")
	@Transactional
	@ResponseStatus(HttpStatus.OK)
	public void disable(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody TwoFactorCodeRequest request
	) {
		User user = loadUser(principal);
		if (!user.isTwoFactorEnabled()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled");
		}
		String secret = totpSecretCipher.decrypt(user);
		if (secret == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is misconfigured");
		}
		if (!totpService.verifyCode(secret, request.code())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid verification code");
		}
		user.setTwoFactorEnabled(false);
		totpSecretCipher.clear(user);
		userRepository.save(user);
	}

	@GetMapping("/status")
	@Transactional(readOnly = true)
	public TwoFactorStatusResponse status(@AuthenticationPrincipal AuthenticatedUser principal) {
		User user = loadUser(principal);
		return new TwoFactorStatusResponse(user.isTwoFactorEnabled());
	}

	private User loadUser(AuthenticatedUser principal) {
		return userRepository.findById(principal.id())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));
	}
}
