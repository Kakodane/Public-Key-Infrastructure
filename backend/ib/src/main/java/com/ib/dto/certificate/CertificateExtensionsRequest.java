package com.ib.dto.certificate;

import jakarta.validation.constraints.Min;

import java.util.Set;

public record CertificateExtensionsRequest(
		Boolean isCa,
		@Min(0) Integer pathLenConstraint,
		Set<CertificateKeyUsage> keyUsages,
		Set<CertificateExtendedKeyUsage> extendedKeyUsages,
        Set<String> subjectAlternativeNames
) {
}

