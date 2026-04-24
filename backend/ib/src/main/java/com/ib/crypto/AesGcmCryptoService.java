package com.ib.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Service
public class AesGcmCryptoService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int NONCE_BYTES = 12;
	private static final int AUTH_TAG_BITS = 128;

	private final SecureRandom secureRandom = new SecureRandom();

	public AesGcmResult encrypt(byte[] plaintext, SecretKey key) {
		if (plaintext == null) {
			throw new IllegalArgumentException("plaintext must not be null.");
		}
		if (key == null) {
			throw new IllegalArgumentException("key must not be null.");
		}
		byte[] nonce = new byte[NONCE_BYTES];
		secureRandom.nextBytes(nonce);
		byte[] ciphertext = runCipher(Cipher.ENCRYPT_MODE, key, nonce, plaintext);
		return new AesGcmResult(ciphertext, nonce);
	}

	public byte[] decrypt(byte[] ciphertext, byte[] nonce, SecretKey key) {
		if (ciphertext == null) {
			throw new IllegalArgumentException("ciphertext must not be null.");
		}
		if (nonce == null || nonce.length != NONCE_BYTES) {
			throw new IllegalArgumentException("nonce must be exactly " + NONCE_BYTES + " bytes.");
		}
		if (key == null) {
			throw new IllegalArgumentException("key must not be null.");
		}
		return runCipher(Cipher.DECRYPT_MODE, key, nonce, ciphertext);
	}

	private static byte[] runCipher(int mode, SecretKey key, byte[] nonce, byte[] input) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(mode, key, new GCMParameterSpec(AUTH_TAG_BITS, nonce));
			return cipher.doFinal(input);
		} catch (GeneralSecurityException ex) {
			throw new CryptoOperationException("AES-GCM operation failed.", ex);
		}
	}

	public record AesGcmResult(byte[] ciphertext, byte[] nonce) {
	}
}
