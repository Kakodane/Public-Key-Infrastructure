package com.ib.crypto;

import com.ib.domain.entity.MasterKey;
import com.ib.domain.repository.MasterKeyRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Seeds master key version 1 from {@code crypto.master-key-base64} when the DB has no keys,
 * then backfills version columns on rows encrypted before versioning was introduced.
 */
@Component
@Order(1)
public class MasterKeyBootstrap implements CommandLineRunner {

	private static final int MASTER_KEY_BYTES = 32;
	private static final int INITIAL_VERSION = 1;

	private final MasterKeyRepository masterKeyRepository;
	private final OrganizationRepository organizationRepository;
	private final UserRepository userRepository;
	private final CryptoProperties cryptoProperties;

	public MasterKeyBootstrap(MasterKeyRepository masterKeyRepository,
	                            OrganizationRepository organizationRepository,
	                            UserRepository userRepository,
	                            CryptoProperties cryptoProperties) {
		this.masterKeyRepository = masterKeyRepository;
		this.organizationRepository = organizationRepository;
		this.userRepository = userRepository;
		this.cryptoProperties = cryptoProperties;
	}

	@Override
	@Transactional
	public void run(String... args) {
		seedInitialMasterKeyIfAbsent();
		backfillEncryptedRowVersions();
	}

	private void seedInitialMasterKeyIfAbsent() {
		if (masterKeyRepository.count() > 0) {
			return;
		}

		byte[] keyMaterial = decodeBase64(cryptoProperties.masterKeyBase64());
		try {
			validateKeyLength(keyMaterial);
			masterKeyRepository.save(MasterKey.builder()
					.version(INITIAL_VERSION)
					.keyMaterial(Arrays.copyOf(keyMaterial, keyMaterial.length))
					.active(true)
					.createdAt(Instant.now())
					.build());
		} finally {
			Arrays.fill(keyMaterial, (byte) 0);
		}
	}

	private void backfillEncryptedRowVersions() {
		organizationRepository.backfillMasterKeyVersionWhereMissing(INITIAL_VERSION);
		userRepository.backfillTotpMasterKeyVersionWhereMissing(INITIAL_VERSION);
	}

	private static byte[] decodeBase64(String keyBase64) {
		try {
			return Base64.getDecoder().decode(keyBase64);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Invalid cryptographic configuration.", ex);
		}
	}

	private static void validateKeyLength(byte[] keyBytes) {
		if (keyBytes.length != MASTER_KEY_BYTES) {
			throw new IllegalStateException("Invalid cryptographic configuration.");
		}
	}
}
