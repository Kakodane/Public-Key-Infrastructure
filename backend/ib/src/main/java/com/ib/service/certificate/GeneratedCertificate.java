package com.ib.service.certificate;

public record GeneratedCertificate(
		String certificatePem,
		byte[] pkcs8PrivateKey,
		String serialNumber
) {
}
