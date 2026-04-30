package com.ib.dto.certificate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateIntermediateCertificateRequest(
		@NotNull Long issuerCertificateId,
		@NotNull Long organizationId,
		@NotNull @Valid X500NameRequest subject,
		@NotNull Instant notBefore,
		@NotNull Instant notAfter,
		@Valid CertificateExtensionsRequest extensions
) {
}
