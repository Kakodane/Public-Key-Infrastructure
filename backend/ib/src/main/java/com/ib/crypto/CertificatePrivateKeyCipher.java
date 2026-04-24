package com.ib.crypto;

import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.repository.CertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;

@Service
public class CertificatePrivateKeyCipher {

	private final OrganizationKeyService organizationKeyService;
	private final AesGcmCryptoService cryptoService;
	private final CertificateRepository certificateRepository;

	public CertificatePrivateKeyCipher(OrganizationKeyService organizationKeyService,
	                                   AesGcmCryptoService cryptoService,
	                                   CertificateRepository certificateRepository) {
		this.organizationKeyService = organizationKeyService;
		this.cryptoService = cryptoService;
		this.certificateRepository = certificateRepository;
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

	@Transactional(readOnly = true)
	public byte[] loadPrivateKey(Long certId) {
		if (certId == null) {
			throw new IllegalArgumentException("certId must not be null.");
		}
		Certificate certificate = certificateRepository.findById(certId)
				.orElseThrow(() -> new IllegalStateException("Certificate private key is unavailable."));
		return decryptPrivateKey(certificate);
	}

	private byte[] decryptPrivateKey(Certificate certificate) {
		if (certificate == null) {
			throw new IllegalArgumentException("certificate must not be null.");
		}
		if (certificate.getEncryptedPrivateKey() == null
				|| certificate.getPrivateKeyNonce() == null
				|| certificate.getPrivateKeyKeyVersion() == null) {
			throw new IllegalStateException("Certificate private key is unavailable.");
		}
		if (certificate.getPrivateKeyKeyVersion() != OrganizationKeyService.CURRENT_KEY_VERSION) {
			throw new CryptoOperationException("Certificate private key cannot be decrypted.", null);
		}
		Organization organization = certificate.getOrganization();
		if (organization == null) {
			throw new IllegalStateException("Certificate private key is unavailable.");
		}
		SecretKey orgKey = organizationKeyService.getOrCreateOrgKey(organization);
		return cryptoService.decrypt(
				certificate.getEncryptedPrivateKey(),
				certificate.getPrivateKeyNonce(),
				orgKey);
	}
}
