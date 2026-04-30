package com.ib.service.certificate;

import com.ib.domain.entity.Certificate;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

@Service
public class CertificateIssuerValidator {

	public void validateForIssuing(Certificate issuer, Instant childNotBefore, Instant childNotAfter) {
		if (issuer == null) {
			throw new IllegalArgumentException("issuer must not be null.");
		}
		if (childNotBefore == null || childNotAfter == null) {
			throw new IllegalArgumentException("Child validity window must be provided.");
		}
		if (!childNotBefore.isBefore(childNotAfter)) {
			throw new CertificateIssuanceException(
					"Child certificate notBefore must be strictly before notAfter.");
		}

		if (issuer.getStatus() != CertificateStatus.ACTIVE) {
			throw new CertificateIssuanceException(
					"Issuer certificate is not active (status=" + issuer.getStatus() + ").");
		}
		if (issuer.getRevokedAt() != null) {
			throw new CertificateIssuanceException("Issuer certificate is revoked.");
		}

		if (issuer.getType() == CertificateType.END_ENTITY) {
			throw new CertificateIssuanceException(
					"End-entity certificates cannot issue other certificates.");
		}

		Instant now = Instant.now();
		if (issuer.getNotBefore() == null || issuer.getNotAfter() == null) {
			throw new CertificateIssuanceException("Issuer validity period is missing.");
		}
		if (now.isBefore(issuer.getNotBefore())) {
			throw new CertificateIssuanceException("Issuer certificate is not yet valid.");
		}
		if (now.isAfter(issuer.getNotAfter())) {
			throw new CertificateIssuanceException("Issuer certificate has expired.");
		}

		if (childNotBefore.isBefore(issuer.getNotBefore())) {
			throw new CertificateIssuanceException(
					"Child notBefore cannot predate the issuer's notBefore.");
		}
		if (childNotAfter.isAfter(issuer.getNotAfter())) {
			throw new CertificateIssuanceException(
					"Child notAfter cannot extend beyond the issuer's notAfter.");
		}

		validateIssuerSignatureChainStrict(issuer);
	}

	private void validateIssuerSignatureChainStrict(Certificate issuer) {
		Set<Long> visitedIds = new HashSet<>();
		Certificate current = issuer;

		// Hard bound to prevent infinite loops if data is corrupted.
		for (int depth = 0; depth < 16; depth++) {
			if (current.getId() != null && !visitedIds.add(current.getId())) {
				throw new CertificateIssuanceException("Issuer chain contains a cycle.");
			}

			if (current.getStatus() != CertificateStatus.ACTIVE) {
				throw new CertificateIssuanceException(
						"Issuer chain contains a non-active certificate (status=" + current.getStatus() + ").");
			}
			if (current.getRevokedAt() != null) {
				throw new CertificateIssuanceException("Issuer chain contains a revoked certificate.");
			}

			X509Certificate currentCert = parsePemX509Strict(current.getCertificatePem(), "issuer certificate PEM");
			Certificate parent = current.getIssuer();

			if (parent == null) {
				verifySelfSignedStrict(currentCert);
				return;
			}

			X509Certificate parentCert = parsePemX509Strict(parent.getCertificatePem(), "parent issuer certificate PEM");
			verifySignedByStrict(currentCert, parentCert.getPublicKey());
			current = parent;
		}

		throw new CertificateIssuanceException("Issuer chain is too deep or malformed.");
	}

	private static X509Certificate parsePemX509Strict(String pem, String label) {
		if (pem == null || pem.isBlank()) {
			throw new CertificateIssuanceException(label + " is missing.");
		}
		try {
			String normalized = pem
					.replace("-----BEGIN CERTIFICATE-----", "")
					.replace("-----END CERTIFICATE-----", "")
					.replaceAll("\\s+", "");
			byte[] der = Base64.getDecoder().decode(normalized);
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
		} catch (IllegalArgumentException e) {
			throw new CertificateIssuanceException("Invalid base64 in " + label + ".", e);
		} catch (CertificateException e) {
			throw new CertificateIssuanceException("Unable to parse " + label + ".", e);
		}
	}

	private static void verifySelfSignedStrict(X509Certificate cert) {
		try {
			PublicKey publicKey = cert.getPublicKey();
			cert.verify(publicKey);
		} catch (Exception e) {
			throw new CertificateIssuanceException("Root issuer certificate is not self-signed or signature is invalid.", e);
		}
	}

	private static void verifySignedByStrict(X509Certificate child, PublicKey issuerPublicKey) {
		try {
			child.verify(issuerPublicKey);
		} catch (Exception e) {
			throw new CertificateIssuanceException("Issuer certificate signature verification failed.", e);
		}
	}
}
