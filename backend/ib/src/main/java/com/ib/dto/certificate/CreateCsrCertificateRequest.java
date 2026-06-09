package com.ib.dto.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateCsrCertificateRequest(
		@NotBlank String csrPem,
		@NotNull Long issuerCertificateId,
		@NotNull Instant notBefore,
		@NotNull Instant notAfter
) {
}
