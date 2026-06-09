package com.ib.crypto;

import com.ib.domain.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class TotpSecretCipher {

	private static final int LEGACY_MASTER_KEY_VERSION = 1;

	private final AesGcmCryptoService cryptoService;
	private final MasterKeyProvider masterKeyProvider;

	public TotpSecretCipher(AesGcmCryptoService cryptoService, MasterKeyProvider masterKeyProvider) {
		this.cryptoService = cryptoService;
		this.masterKeyProvider = masterKeyProvider;
	}

	public void encryptAndStore(User user, String plaintextSecret) {
		byte[] plainBytes = plaintextSecret.getBytes(StandardCharsets.UTF_8);
		AesGcmCryptoService.AesGcmResult result = cryptoService.encrypt(plainBytes, masterKeyProvider.getCurrentKey());
		user.setEncryptedTotpSecret(result.ciphertext());
		user.setTotpSecretNonce(result.nonce());
		user.setTotpMasterKeyVersion(masterKeyProvider.getCurrentVersion());
	}

	public String decrypt(User user) {
		if (user.getEncryptedTotpSecret() == null || user.getTotpSecretNonce() == null) {
			return null;
		}
		byte[] plainBytes = cryptoService.decrypt(
				user.getEncryptedTotpSecret(),
				user.getTotpSecretNonce(),
				masterKeyProvider.getKey(resolveMasterKeyVersion(user.getTotpMasterKeyVersion()))
		);
		return new String(plainBytes, StandardCharsets.UTF_8);
	}

	public void rewrapTotpSecret(User user, int fromVersion, int toVersion) {
		if (user.getEncryptedTotpSecret() == null || user.getTotpSecretNonce() == null) {
			throw new IllegalArgumentException("User has no encrypted TOTP secret.");
		}
		if (resolveMasterKeyVersion(user.getTotpMasterKeyVersion()) != fromVersion) {
			throw new IllegalArgumentException(
					"User TOTP master key version mismatch: expected " + fromVersion + ".");
		}
		byte[] plainBytes = cryptoService.decrypt(
				user.getEncryptedTotpSecret(),
				user.getTotpSecretNonce(),
				masterKeyProvider.getKey(fromVersion));
		try {
			AesGcmCryptoService.AesGcmResult result = cryptoService.encrypt(
					plainBytes, masterKeyProvider.getKey(toVersion));
			user.setEncryptedTotpSecret(result.ciphertext());
			user.setTotpSecretNonce(result.nonce());
			user.setTotpMasterKeyVersion(toVersion);
		} finally {
			Arrays.fill(plainBytes, (byte) 0);
		}
	}

	public void clear(User user) {
		user.setEncryptedTotpSecret(null);
		user.setTotpSecretNonce(null);
		user.setTotpMasterKeyVersion(null);
	}

	private static int resolveMasterKeyVersion(Integer masterKeyVersion) {
		return masterKeyVersion != null ? masterKeyVersion : LEGACY_MASTER_KEY_VERSION;
	}
}
