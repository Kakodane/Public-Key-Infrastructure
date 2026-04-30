package com.ib.dto.certificate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEndEntityCertificateRequest(
		@NotNull Long issuerCertificateId,
		@NotNull @Email String userEmail,
		@NotNull @Valid X500NameRequest subject,
		@NotNull Instant notBefore,
		@NotNull Instant notAfter,
		@Valid CertificateExtensionsRequest extensions
) {
}
