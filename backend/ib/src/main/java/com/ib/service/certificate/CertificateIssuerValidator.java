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

	private static final int KEY_USAGE_KEY_CERT_SIGN = 5;

	private static final int MAX_CHAIN_DEPTH = 16;

	public void validateForIssuing(
			Certificate issuer,
			Instant childNotBefore,
			Instant childNotAfter,
			CertificateType childType
	) {
		requireArgs(issuer, childNotBefore, childNotAfter, childType);
		validateChildWindow(childNotBefore, childNotAfter);
		validateIssuerStatusAndType(issuer);
		validateIssuerWindow(issuer, childNotBefore, childNotAfter);
		validateIssuerSignatureChainStrict(issuer);
		validateIssuerExtensions(issuer);

		if (childType == CertificateType.INTERMEDIATE) {
			validatePathLengthBudget(issuer);
		}
	}

	private void requireArgs(Certificate issuer,
	                         Instant childNotBefore,
	                         Instant childNotAfter,
	                         CertificateType childType) {
		if (issuer == null) {
			throw new IllegalArgumentException("issuer must not be null.");
		}
		if (childNotBefore == null || childNotAfter == null) {
			throw new IllegalArgumentException("Child validity window must be provided.");
		}
		if (childType == null) {
			throw new IllegalArgumentException("Child certificate type must be provided.");
		}
	}

	private void validateChildWindow(Instant childNotBefore, Instant childNotAfter) {
		if (!childNotBefore.isBefore(childNotAfter)) {
			throw new CertificateIssuanceException(
					"Child certificate notBefore must be strictly before notAfter.");
		}
	}

	private void validateIssuerStatusAndType(Certificate issuer) {
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
	}

	private void validateIssuerWindow(Certificate issuer,
	                                  Instant childNotBefore,
	                                  Instant childNotAfter) {
		if (issuer.getNotBefore() == null || issuer.getNotAfter() == null) {
			throw new CertificateIssuanceException("Issuer validity period is missing.");
		}
		Instant now = Instant.now();
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
	}

	private void validateIssuerSignatureChainStrict(Certificate issuer) {
		Set<Long> visitedIds = new HashSet<>();
		Certificate current = issuer;

		for (int depth = 0; depth < MAX_CHAIN_DEPTH; depth++) {
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

			X509Certificate currentCert = parsePem(current.getCertificatePem(), "issuer certificate PEM");
			Certificate parent = current.getIssuer();

			if (parent == null) {
				verifySelfSigned(currentCert);
				return;
			}

			X509Certificate parentCert = parsePem(parent.getCertificatePem(), "parent issuer certificate PEM");
			verifySignedBy(currentCert, parentCert.getPublicKey());
			current = parent;
		}

		throw new CertificateIssuanceException("Issuer chain is too deep or malformed.");
	}

	private void validateIssuerExtensions(Certificate issuer) {
		X509Certificate x509 = parsePem(issuer.getCertificatePem(), "issuer certificate PEM");

		if (x509.getBasicConstraints() < 0) {
			throw new CertificateIssuanceException(
					"Issuer is not a CA (BasicConstraints CA:true is missing).");
		}

		boolean[] keyUsage = x509.getKeyUsage();
		if (keyUsage == null
				|| keyUsage.length <= KEY_USAGE_KEY_CERT_SIGN
				|| !keyUsage[KEY_USAGE_KEY_CERT_SIGN]) {
			throw new CertificateIssuanceException(
					"Issuer KeyUsage must include keyCertSign.");
		}
	}

	private void validatePathLengthBudget(Certificate issuer) {
		int remaining = computeRemainingPathLength(issuer);
		if (remaining <= 0) {
			throw new CertificateIssuanceException(
					"Path length budget exhausted — this chain cannot accept another intermediate CA.");
		}
	}

	private int computeRemainingPathLength(Certificate issuer) {
		int min = Integer.MAX_VALUE;
		int distance = 0;
		Certificate cur = issuer;
		while (cur != null) {
			int pl = parsePem(cur.getCertificatePem(), "issuer certificate PEM").getBasicConstraints();
			if (pl != Integer.MAX_VALUE && pl >= 0) {
				int remaining = pl - distance;
				if (remaining < min) {
					min = remaining;
				}
			}
			cur = cur.getIssuer();
			distance++;
		}
		return min;
	}

	private static X509Certificate parsePem(String pem, String label) {
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

	private static void verifySelfSigned(X509Certificate cert) {
		try {
			cert.verify(cert.getPublicKey());
		} catch (Exception e) {
			throw new CertificateIssuanceException(
					"Root issuer certificate is not self-signed or signature is invalid.", e);
		}
	}

	private static void verifySignedBy(X509Certificate child, PublicKey issuerPublicKey) {
		try {
			child.verify(issuerPublicKey);
		} catch (Exception e) {
			throw new CertificateIssuanceException("Issuer certificate signature verification failed.", e);
		}
	}
}
