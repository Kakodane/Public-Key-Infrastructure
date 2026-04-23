package com.ib.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

@Component
public class MasterKeyProvider {

	private static final int MASTER_KEY_BYTES = 32;
	private static final String AES_ALGORITHM = "AES";

	private final SecretKey masterKey;

	public MasterKeyProvider(CryptoProperties cryptoProperties) {
		byte[] decodedKey = decodeBase64(cryptoProperties.masterKeyBase64());
		validateKeyLength(decodedKey);
		this.masterKey = new SecretKeySpec(decodedKey, AES_ALGORITHM);
		Arrays.fill(decodedKey, (byte) 0);
	}

	public SecretKey masterKey() {
		return masterKey;
	}

	private static byte[] decodeBase64(String keyBase64) {
		try {
			return Base64.getDecoder().decode(keyBase64);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Invalid crypto.master-key-base64 value.", ex);
		}
	}

	private static void validateKeyLength(byte[] keyBytes) {
		if (keyBytes.length != MASTER_KEY_BYTES) {
			throw new IllegalStateException("crypto.master-key-base64 must decode to exactly 32 bytes.");
		}
	}
}
