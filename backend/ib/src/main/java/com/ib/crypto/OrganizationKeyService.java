package com.ib.crypto;

import com.ib.domain.entity.Organization;
import com.ib.domain.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Service
public class OrganizationKeyService {

	private static final String AES_ALGORITHM = "AES";
	private static final int ORG_KEY_BITS = 256;
	private static final int LEGACY_MASTER_KEY_VERSION = 1;
	static final int CURRENT_KEY_VERSION = 1;

	private final MasterKeyProvider masterKeyProvider;
	private final AesGcmCryptoService cryptoService;
	private final OrganizationRepository organizationRepository;

	public OrganizationKeyService(MasterKeyProvider masterKeyProvider,
	                              AesGcmCryptoService cryptoService,
	                              OrganizationRepository organizationRepository) {
		this.masterKeyProvider = masterKeyProvider;
		this.cryptoService = cryptoService;
		this.organizationRepository = organizationRepository;
	}

	@Transactional
	public SecretKey getOrCreateOrgKey(Organization organization) {
		if (organization == null) {
			throw new IllegalArgumentException("organization must not be null.");
		}
		if (hasWrappedKey(organization)) {
			return unwrap(organization);
		}
		return generateAndPersist(organization);
	}

	private boolean hasWrappedKey(Organization organization) {
		return organization.getEncryptedOrgKey() != null
				&& organization.getOrgKeyNonce() != null
				&& organization.getOrgKeyVersion() != null;
	}

	private SecretKey unwrap(Organization organization) {
		byte[] rawKey = cryptoService.decrypt(
				organization.getEncryptedOrgKey(),
				organization.getOrgKeyNonce(),
				masterKeyProvider.getKey(resolveMasterKeyVersion(organization.getMasterKeyVersion())));
		try {
			return new SecretKeySpec(rawKey, AES_ALGORITHM);
		} finally {
			Arrays.fill(rawKey, (byte) 0);
		}
	}

	private SecretKey generateAndPersist(Organization organization) {
		byte[] rawKey = generateAesKeyBytes();
		try {
			AesGcmCryptoService.AesGcmResult wrapped = cryptoService.encrypt(
					rawKey, masterKeyProvider.getCurrentKey());
			organization.setEncryptedOrgKey(wrapped.ciphertext());
			organization.setOrgKeyNonce(wrapped.nonce());
			organization.setOrgKeyVersion(CURRENT_KEY_VERSION);
			organization.setMasterKeyVersion(masterKeyProvider.getCurrentVersion());
			organizationRepository.save(organization);
			return new SecretKeySpec(rawKey, AES_ALGORITHM);
		} finally {
			Arrays.fill(rawKey, (byte) 0);
		}
	}

	public void rewrapOrgKey(Organization organization, int fromVersion, int toVersion) {
		if (!hasWrappedKey(organization)) {
			throw new IllegalArgumentException("Organization has no wrapped org key.");
		}
		if (resolveMasterKeyVersion(organization.getMasterKeyVersion()) != fromVersion) {
			throw new IllegalArgumentException(
					"Organization master key version mismatch: expected " + fromVersion + ".");
		}
		byte[] rawKey = cryptoService.decrypt(
				organization.getEncryptedOrgKey(),
				organization.getOrgKeyNonce(),
				masterKeyProvider.getKey(fromVersion));
		try {
			AesGcmCryptoService.AesGcmResult wrapped = cryptoService.encrypt(
					rawKey, masterKeyProvider.getKey(toVersion));
			organization.setEncryptedOrgKey(wrapped.ciphertext());
			organization.setOrgKeyNonce(wrapped.nonce());
			organization.setMasterKeyVersion(toVersion);
		} finally {
			Arrays.fill(rawKey, (byte) 0);
		}
	}

	private static int resolveMasterKeyVersion(Integer masterKeyVersion) {
		return masterKeyVersion != null ? masterKeyVersion : LEGACY_MASTER_KEY_VERSION;
	}

	private static byte[] generateAesKeyBytes() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
			keyGenerator.init(ORG_KEY_BITS);
			return keyGenerator.generateKey().getEncoded();
		} catch (NoSuchAlgorithmException ex) {
			throw new CryptoOperationException("AES key generation failed.", ex);
		}
	}
}
