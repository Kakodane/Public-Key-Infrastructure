package com.ib.dto.certificate;

import com.ib.dto.auth.GetCertificateResponse;

public record AutogenerateEeCertificateResponse(
		GetCertificateResponse certificate,
		String keystoreBase64,
		String keystoreFilename
) {
}
