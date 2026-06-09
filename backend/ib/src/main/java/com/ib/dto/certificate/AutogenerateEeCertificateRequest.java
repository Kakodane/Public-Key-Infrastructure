package com.ib.dto.certificate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AutogenerateEeCertificateRequest(
		@NotNull Long issuerCertificateId,
		@NotNull @Valid X500NameRequest subject,
		@NotNull Instant notBefore,
		@NotNull Instant notAfter,
		@NotBlank String keystoreFormat,
		@NotBlank @Size(min = 8, max = 128) String keystorePassword
) {
}
