package com.ib.crypto;

import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class CertificatePrivateKeyCipher {

	private final OrganizationKeyService organizationKeyService;
	private final AesGcmCryptoService cryptoService;

	public CertificatePrivateKeyCipher(OrganizationKeyService organizationKeyService,
	                                   AesGcmCryptoService cryptoService) {
		this.organizationKeyService = organizationKeyService;
		this.cryptoService = cryptoService;
	}

	public EncryptedPayload encryptPrivateKey(byte[] pkcs8PrivateKey, Organization organization) {
		if (pkcs8PrivateKey == null || pkcs8PrivateKey.length == 0) {
			throw new IllegalArgumentException("pkcs8PrivateKey must not be empty.");
		}
		if (organization == null) {
			throw new IllegalArgumentException("organization must not be null.");
		}
		SecretKey orgKey = organizationKeyService.getOrCreateOrgKey(organization);
		AesGcmCryptoService.AesGcmResult result = cryptoService.encrypt(pkcs8PrivateKey, orgKey);
		return new EncryptedPayload(
				result.ciphertext(),
				result.nonce(),
				OrganizationKeyService.CURRENT_KEY_VERSION);
	}

	/**
	 * Single entry point for the certificate issuing path: encrypts the freshly generated
	 * PKCS#8 private key with the organization data key and writes only the ciphertext,
	 * nonce, and key version onto the {@link Certificate}. The plaintext is never assigned
	 * to the entity, so no issuer code path can accidentally persist it.
	 */
	public void attachEncryptedPrivateKey(Certificate certificate, byte[] pkcs8PrivateKey) {
		if (certificate == null) {
			throw new IllegalArgumentException("certificate must not be null.");
		}
		Organization organization = certificate.getOrganization();
		if (organization == null) {
			throw new IllegalStateException(
					"Certificate must be attached to an organization before encrypting its private key.");
		}
		EncryptedPayload payload = encryptPrivateKey(pkcs8PrivateKey, organization);
		certificate.setEncryptedPrivateKey(payload.ciphertext());
		certificate.setPrivateKeyNonce(payload.nonce());
		certificate.setPrivateKeyKeyVersion(payload.keyVersion());
	}

	public byte[] decryptPrivateKey(Certificate certificate) {
		if (certificate == null) {
			throw new IllegalArgumentException("certificate must not be null.");
		}
		if (certificate.getEncryptedPrivateKey() == null
				|| certificate.getPrivateKeyNonce() == null
				|| certificate.getPrivateKeyKeyVersion() == null) {
			throw new IllegalStateException("Certificate has no encrypted private key.");
		}
		if (certificate.getPrivateKeyKeyVersion() != OrganizationKeyService.CURRENT_KEY_VERSION) {
			throw new CryptoOperationException(
					"Unsupported private key version: " + certificate.getPrivateKeyKeyVersion(), null);
		}
		Organization organization = certificate.getOrganization();
		if (organization == null) {
			throw new IllegalStateException("Certificate is not attached to an organization.");
		}
		SecretKey orgKey = organizationKeyService.getOrCreateOrgKey(organization);
		return cryptoService.decrypt(
				certificate.getEncryptedPrivateKey(),
				certificate.getPrivateKeyNonce(),
				orgKey);
	}
}
