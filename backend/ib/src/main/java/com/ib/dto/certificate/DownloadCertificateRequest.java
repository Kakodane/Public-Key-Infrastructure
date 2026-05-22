package com.ib.dto.certificate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DownloadCertificateRequest(
		@NotBlank
		@Pattern(regexp = "pem|cer|pkcs12", flags = Pattern.Flag.CASE_INSENSITIVE, message = "format must be pem, cer, or pkcs12")
		String format,

		@Size(min = 8, max = 128, message = "Keystore password must be 8-128 characters")
		String keystorePassword
) {

	@AssertTrue(message = "Keystore password is required when format is pkcs12")
	boolean isKeystorePasswordPresentForPkcs12() {
		if (!"pkcs12".equalsIgnoreCase(format)) {
			return true;
		}
		return keystorePassword != null && !keystorePassword.isBlank();
	}
}
