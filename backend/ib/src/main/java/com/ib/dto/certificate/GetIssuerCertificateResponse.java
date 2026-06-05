package com.ib.dto.certificate;

import com.ib.domain.entity.Certificate;

import java.time.Instant;

public record GetIssuerCertificateResponse(
		Long id,
		String serialNumber,
		String subjectCommonName,
		String organizationName,
		String type,
		Instant notBefore,
		Instant notAfter
) {
	public GetIssuerCertificateResponse(Certificate certificate) {
		this(
				certificate.getId(),
				certificate.getSerialNumber(),
				certificate.getSubject().getCommonName(),
				certificate.getOrganization().getName(),
				certificate.getType().name(),
				certificate.getNotBefore(),
				certificate.getNotAfter()
		);
	}
}
