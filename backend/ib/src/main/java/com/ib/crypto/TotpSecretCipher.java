package com.ib.crypto;

import com.ib.domain.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class TotpSecretCipher {

	private final AesGcmCryptoService cryptoService;
	private final MasterKeyProvider masterKeyProvider;

	public TotpSecretCipher(AesGcmCryptoService cryptoService, MasterKeyProvider masterKeyProvider) {
		this.cryptoService = cryptoService;
		this.masterKeyProvider = masterKeyProvider;
	}

	public void encryptAndStore(User user, String plaintextSecret) {
		byte[] plainBytes = plaintextSecret.getBytes(StandardCharsets.UTF_8);
		AesGcmCryptoService.AesGcmResult result = cryptoService.encrypt(plainBytes, masterKeyProvider.masterKey());
		user.setEncryptedTotpSecret(result.ciphertext());
		user.setTotpSecretNonce(result.nonce());
	}

	public String decrypt(User user) {
		if (user.getEncryptedTotpSecret() == null || user.getTotpSecretNonce() == null) {
			return null;
		}
		byte[] plainBytes = cryptoService.decrypt(
				user.getEncryptedTotpSecret(),
				user.getTotpSecretNonce(),
				masterKeyProvider.masterKey()
		);
		return new String(plainBytes, StandardCharsets.UTF_8);
	}

	public void clear(User user) {
		user.setEncryptedTotpSecret(null);
		user.setTotpSecretNonce(null);
	}
}
