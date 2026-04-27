package com.ib.service.certificate;

import com.ib.domain.entity.Certificate;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
	}
}
