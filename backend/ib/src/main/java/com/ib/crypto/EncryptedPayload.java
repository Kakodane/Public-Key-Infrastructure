package com.ib.crypto;

public record EncryptedPayload(byte[] ciphertext, byte[] nonce, int keyVersion) {

	public EncryptedPayload {
		if (ciphertext == null || ciphertext.length == 0) {
			throw new IllegalArgumentException("ciphertext must not be empty.");
		}
		if (nonce == null || nonce.length == 0) {
			throw new IllegalArgumentException("nonce must not be empty.");
		}
		if (keyVersion < 1) {
			throw new IllegalArgumentException("keyVersion must be >= 1.");
		}
	}
}
